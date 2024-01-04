package ca.ualberta.autowise.scripts.webhook


import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import java.time.format.DateTimeFormatter


import static ca.ualberta.autowise.scripts.ManageEventStatusTable.*
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.*
import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.getShiftRole
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.*
import static ca.ualberta.autowise.scripts.google.SendEmail.*
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.*

@Field static DateTimeFormatter eventDayFormatter = DateTimeFormatter.ofPattern("M/dd/yyyy")
@Field static def log = LoggerFactory.getLogger(SignupForRoleShift.class)

/**
 * @Author Alexandru Ianta
 * When a volunteer clicks the sign-up link for a particular shift role:
 * 1. Look through the 'Event Status' sheet
 *     a) go line by line until we find entries for the shift role associate with the link they pressed.
 *     if there is an empty slot:
 *          1. Put the volunteer's email in the slot and update the event status table.
 *          2. Update the Volunteer Contact status table.
 *          3. Create a cancellation webhook for the volunteer.
 *          4. Send a confirmation email to the volunteer.
 *          5. Notify the event slack channel.
 *          6. Mark this webhook invoked
 *     b) There is no empty slot
 *          1. Update the volunteer contact status table
 *          2. Send a waitlist confirmation email to the volunteer.
 *          3. Notify the event slack channel.
 *          4. Mark this webhook invoked
 */


static def acceptShiftRole(services, Webhook webhook, Event event,  config){

        def volunteerEmail = webhook.data.getString "volunteerEmail"
        def eventId = webhook.eventId;

        CompositeFuture.all(
                hasVolunteerAlreadySignedUp(services.db, eventId, volunteerEmail),
                hasVolunteerAlreadyCancelled(services.db, eventId, volunteerEmail)
        ).compose{
            volunteerCheck->
                def alreadySignedUp = volunteerCheck.resultAt(0)
                def alreadyCancelled = volunteerCheck.resultAt(1)

                if(alreadySignedUp){
                    log.info "${volunteerEmail} has already signed up fo a different shift-role. Ignoring this request."
                    return Future.failedFuture("You have already signed up for a different shift-role. Ignoring this request.")
                }

                if(alreadyCancelled){
                    log.info "${volunteerEmail} has already cancelled or rejected for this event."
                    return Future.failedFuture("You have already cancelled on a shift-role for this event. Ignoring this request.")
                }

                def targetShiftRoleString = webhook.data.getString("shiftRoleString")
                def volunteerName = webhook.data.getString "volunteerName"

                ShiftRole shiftRole = getShiftRole(targetShiftRoleString, event.roles)

                return services.db.assignShiftRole(event.sheetId, targetShiftRoleString, volunteerEmail, volunteerName).compose {
                    if(it){
                        //The volunteer has been successfully assigned
                        return updateVolunteerStatus(services.db, event.id, event.sheetId, volunteerEmail, "Accepted", targetShiftRoleString)

                            .compose {
                                Webhook cancelHook = new Webhook(
                                        id: UUID.randomUUID(),
                                        eventId: webhook.eventId,
                                        type: HookType.CANCEL_ROLE_SHIFT,
                                        expiry: event.startTime.toInstant().toEpochMilli(),
                                        invoked: false,
                                        data: new JsonObject()
                                                .put("volunteerEmail", volunteerEmail)
                                                .put("shiftRoleString", targetShiftRoleString)
                                                .put("volunteerName", volunteerName)
                                )
                                services.db.insertWebhook(cancelHook)
                                services.server.mountWebhook(cancelHook)

                                return slurpDocument(services.googleAPI, event.confirmAssignedEmailTemplateId).compose{
                                    emailTemplate ->
                                        def emailContents = makeAssignedEmail(emailTemplate, shiftRole, event.name, event.startTime, cancelHook, config)

                                        log.info "email contents: \n${emailContents}"

                                        return CompositeFuture.all(
                                                updateEventStatusTable(services, event.sheetId),
                                                sendEmail(config, services.googleAPI, config.getString("sender_email"), volunteerEmail, "[WiSER] Volunteer Sign-up Confirmation for ${event.name}", emailContents),
                                                sendSlackMessage(services.slackAPI, event.eventSlackChannel, "${volunteerName} has expressed interest in volunteering for ${event.name} as ${shiftRole.role.name}. They have been successfully assigned shift ${shiftRole.shift.index} starting at ${shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter)} and ending at ${shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter)}. "  ) // Notify Slack
                                        )
                                }
                            }

                    }else{

                        //If we make it to here, there are no available slots for the target shift role.
                        CompositeFuture.all(
                                updateVolunteerStatus(services.db,eventId,  event.sheetId, volunteerEmail, "Waitlisted", targetShiftRoleString).compose { return updateEventStatusTable(services, event.sheetId)},
                                slurpDocument(services.googleAPI, event.confirmWaitlistEmailTemplateId)
                        ).compose{
                            compositeResult->
                                def emailTemplate = compositeResult.resultAt(1)
                                def emailContents = makeWaitlistEmail(emailTemplate, shiftRole, event.name)

                                return CompositeFuture.all(
                                        sendEmail(config, services.googleAPI, config.getString("sender_email"), volunteerEmail, "[WiSER] Volunteer Sign-up Waitlist Confirmation for ${event.name}", emailContents),
                                        sendSlackMessage(services.slackAPI, event.eventSlackChannel, "${volunteerName} has expressed interest in volunteering for ${event.name} as ${shiftRole.role.name}. However there were no matching free slots for this volunteer, so they have been waitlisted and notified that they will be contacted if a slot frees up.")
                                )
                        }
                    }
                }


        }

}

static def makeAssignedEmail(template, ShiftRole shiftRole, eventName, eventStartTime, Webhook cancelHook, config){
    def emailContents = template.replaceAll("%EVENT_NAME%", eventName)
    emailContents = emailContents.replaceAll("%ROLE_NAME%", shiftRole.role.name)
    emailContents = emailContents.replaceAll("%SHIFT_INDEX%", Integer.toString(shiftRole.shift.index))
    emailContents = emailContents.replaceAll("%SHIFT_START%", shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter))
    emailContents = emailContents.replaceAll("%SHIFT_END%", shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter))
    emailContents = emailContents.replaceAll("%EVENT_DATE%", eventStartTime.format(eventDayFormatter))
    emailContents = emailContents.replaceAll("%CANCEL_LINK%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}/${cancelHook.path()}\">Cancel</a>")
    return emailContents
}


static def makeWaitlistEmail(template, ShiftRole shiftRole, eventName){
    def emailContents = template.replaceAll("%EVENT_NAME%", eventName)
    emailContents = emailContents.replaceAll("%ROLE_NAME%", shiftRole.role.name)
    emailContents = emailContents.replaceAll("%SHIFT_START%", shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter))
    emailContents = emailContents.replaceAll("%SHIFT_END%", shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter))
    return emailContents
}