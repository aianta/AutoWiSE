package ca.ualberta.autowise.scripts.webhook

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.WaitlistEntry
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.FindAvailableShiftRoles
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import java.time.ZonedDateTime

import static ca.ualberta.autowise.utils.JsonUtils.slurpRolesJson
import static ca.ualberta.autowise.scripts.ManageEventStatusTable.*
import static ca.ualberta.autowise.scripts.google.GetSheetValue.*
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.*
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.*
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.*
import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.*
import static ca.ualberta.autowise.scripts.google.SendEmail.*
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.*

@Field static def log = LoggerFactory.getLogger(CancelShiftRole.class)

/**
 * @Author Alexandru Ianta
 * When a volunteer cancels on their commitment to volunteer for a shift role:
 * Before we do anything, we need to check that this volunteer hasn't already cancelled.
 *
 * 1. Find them in the event status table and remove their email and name from the list.
 * 2. Update the volunteers status in the volunteer contact status sheet.
 * 3. Read through the volunteer contact status table and find the next waitlisted volunteer for the shift-role.
 *      a) If one exists, move them into the appropriate volunteer slot on the event status sheet.
 *      b) Create a cancel webhook for the waitlisted volunteer
 *      c) Email the replacement volunteer to let them know they have been put into the volunteer slot.
 *      d) Update the replacement volunteer's status in the volunteer contact status sheet.
 * 4. Email the volunteer that cancelled to confirm their cancellation.
 * 5. Notify slack of the changes.
 */

static def cancelShiftRole(services, Webhook webhook, Event event, config){
    def volunteerEmail = webhook.data.getString("volunteerEmail")

    return hasVolunteerAlreadyCancelled(services.db, event.id, volunteerEmail).compose{
        alreadyCancelled->

            if(alreadyCancelled){
                log.info "${volunteerEmail} has already cancelled before halting cancel operation."
                return Future.failedFuture("You have already cancelled on a shift-role for this event.")
            }


            return slurpVolunteerList(services.googleAPI, config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range"))
                .compose{
                    volunteers->
                        def targetShiftRoleString = webhook.data.getString("shiftRoleString")
                        def volunteerName = webhook.data.getString("volunteerName")

                        ShiftRole shiftRole = getShiftRole(targetShiftRoleString, event.roles)


                        def slackMessage = "${volunteerName} has cancelled their ${shiftRole.role.name} shift for ${event.name}."

                        return updateVolunteerStatus(services.db, event.id, event.sheetId, volunteerEmail, "Cancelled", targetShiftRoleString)
                            .compose{

                                getWaitlistForShiftRole(services.db, event.id, targetShiftRoleString).compose {
                                    List<WaitlistEntry> waitlist ->

                                        //If there are volunteers waiting to be put into this shift-role
                                        if (!waitlist.isEmpty()){ //Grab the first one, and enter their info into the event status sheet.
                                            def replacementEmail = waitlist.get(0).email
                                            def replacementName = getVolunteerByEmail(replacementEmail, volunteers).name


                                            //Create a cancel hook for the replacement volunteer
                                            Webhook cancelHook = new Webhook(
                                                    id: UUID.randomUUID(),
                                                    eventId: webhook.eventId,
                                                    type: HookType.CANCEL_ROLE_SHIFT,
                                                    expiry: event.startTime.toInstant().toEpochMilli(),
                                                    invoked: false,
                                                    data: new JsonObject()
                                                            .put("volunteerEmail", replacementEmail)
                                                            .put("volunteerName", replacementName)
                                                            .put("shiftRoleString", targetShiftRoleString)

                                            )
                                            services.db.insertWebhook(cancelHook)
                                            services.server.mountWebhook(cancelHook)

                                            return CompositeFuture.all(
                                                    services.db.assignShiftRole(event.sheetId, targetShiftRoleString, volunteerEmail, replacementEmail, volunteerName, replacementName), //Update the event status table with the replacements info.
                                                    slurpDocument(services.googleAPI,event.confirmAssignedEmailTemplateId), //Get the email template to send to the waitlisted volunteer
                                                    slurpDocument(services.googleAPI, event.confirmCancelledEmailTemplateId) //Get the email template to send to the cancelling volunteer
                                            ).compose{
                                                compositeResult->
                                                    def confirmAssignmentEmailTemplate = compositeResult.resultAt(1)
                                                    def confirmCancelEmailTemplate = compositeResult.resultAt(2)

                                                    def emailContent = confirmAssignmentEmailTemplate.replaceAll("%EVENT_NAME%", event.name)
                                                    emailContent = emailContent.replaceAll("%ROLE_NAME%", shiftRole.role.name )
                                                    emailContent = emailContent.replaceAll("%SHIFT_INDEX%", Integer.toString(shiftRole.shift.index))
                                                    emailContent = emailContent.replaceAll("%SHIFT_START%", shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter))
                                                    emailContent = emailContent.replaceAll("%SHIFT_END%", shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter))
                                                    emailContent = emailContent.replaceAll("%EVENT_DATE%", event.startTime.format(SignupForRoleShift.eventDayFormatter))
                                                    emailContent = emailContent.replaceAll("%CANCEL_LINK%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}/${cancelHook.path()}\">Cancel</a>")

                                                    slackMessage = slackMessage + " ${replacementName} has automatically been moved from the waitlist into the freed slot and notified of their role assignment."

                                                    return CompositeFuture.all(
                                                            sendEmail(config, services.googleAPI, config.getString("sender_email"), replacementEmail, "[WiSER] Moved off waitlist, assigned volunteer role for ${event.name}",emailContent),
                                                            updateVolunteerStatus(services.db, event.id, event.sheetId, replacementEmail, "Accepted", targetShiftRoleString ),
                                                            sendSlackMessage(services.slackAPI, event.eventSlackChannel, slackMessage),
                                                            sendEmail(config, services.googleAPI, config.getString("sender_email"), volunteerEmail,"[WiSER] Volunteering Cancellation Confirmation for ${event.name}", confirmCancelEmailTemplate)
                                                    )

                                            }
                                        }

                                        return CompositeFuture.all(
                                                services.db.assignShiftRole(event.sheetId, targetShiftRoleString, volunteerEmail, null, volunteerName, null), // Clear assignment for shift role to allow it to be filled again.
                                                slurpDocument(services.googleAPI, event.confirmCancelledEmailTemplateId)
                                        ).compose {
                                            compositeResult->
                                                def emailTemplate = compositeResult.resultAt(1)

                                                return CompositeFuture.all(
                                                        sendEmail(config, services.googleAPI, config.getString("sender_email"), volunteerEmail, "[WiSER] Volunteering Cancellation Confirmation for ${event.name}", emailTemplate),
                                                        sendSlackMessage(services.slackAPI, event.eventSlackChannel, slackMessage)
                                                )
                                        }
                                }

                            }


                }

    }

}


