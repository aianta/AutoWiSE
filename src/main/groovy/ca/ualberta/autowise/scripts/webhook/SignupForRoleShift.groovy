package ca.ualberta.autowise.scripts.webhook


import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.FindAvailableShiftRoles
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import java.time.format.DateTimeFormatter

import java.time.ZonedDateTime;

import static ca.ualberta.autowise.JsonUtils.*
import static ca.ualberta.autowise.scripts.google.GetSheetValue.*
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


static def acceptShiftRole(services, Webhook webhook, config){
        def eventSheetId = webhook.data.getString "eventSheetId"
        def volunteerEmail = webhook.data.getString "volunteerEmail"

        if(hasVolunteerAlreadyCancelled(services.googleAPI, eventSheetId, volunteerEmail)){
            log.info "${volunteerEmail} has already cancelled or rejected for this event."
            return
        }

        def targetShiftRoleString = webhook.data.getString("shiftRoleString")

        def volunteerName = webhook.data.getString "volunteerName"

        def eventName = webhook.data.getString "eventName"
        List<Role> roles = slurpRolesJson(webhook.data.getString("rolesJsonString"))
        def eventStartTime = ZonedDateTime.parse(webhook.data.getString("eventStartTime"), EventSlurper.eventTimeFormatter)
        def eventSlackChannel = webhook.data.getString("eventSlackChannel")
        ShiftRole shiftRole = getShiftRole(targetShiftRoleString, roles)

        def data = getValuesAt(services.googleAPI, eventSheetId, FindAvailableShiftRoles.EVENT_STATUS_RANGE )
        def tableFirstColHeader = "Shift - Role"
        def it = data.listIterator()

        //Skip irrelevant rows at the top of the sheet.
        while(it.hasNext()) {
            def rowData = it.next();
            log.info rowData.toString()
            if (rowData.isEmpty() || !rowData.get(0).equals(tableFirstColHeader)) {
                log.info "Skipping row before shift-role header: ${!rowData.isEmpty()?rowData.get(0).equals(tableFirstColHeader):"empty"}"
                continue //Skip all lines until 'Shift - Role' header
            }
            if (rowData.get(0).equals(tableFirstColHeader)){
                break
            }
        }

        while (it.hasNext()){
            def rowData = it.next()

            //Check if shift-role has been filled.
            if ((rowData.size() == 1 || rowData.get(1).equals("") || rowData.get(1).equals("-")) && rowData.get(0).equals(targetShiftRoleString) ){
                //If there is no value beside this shiftRoleString, or the value is empty, or a dash the shift-role has not been filled.
                log.info "Target shift-role ${targetShiftRoleString} has available slot!"

                if (rowData.size() < 3){
                    rowData.add(volunteerEmail)
                    rowData.add(volunteerName)
                }else{
                    rowData.set(1, volunteerEmail) //Assign this volunteer to the shift-role
                    rowData.set(2, volunteerName)
                }

                updateEventStatusTable(services.googleAPI, eventSheetId, data)
                updateVolunteerStatus(services.googleAPI, eventSheetId, volunteerEmail, "Accepted", targetShiftRoleString)

                Webhook cancelHook = new Webhook(
                        id: UUID.randomUUID(),
                        eventId: webhook.eventId,
                        type: HookType.CANCEL_ROLE_SHIFT,
                        expiry: eventStartTime.toInstant().toEpochMilli(),
                        invoked: false,
                        data: new JsonObject()
                            .put("volunteerEmail", volunteerEmail)
                            .put("eventSheetId", eventSheetId)
                            .put("eventStartTime", eventStartTime.format(EventSlurper.eventTimeFormatter))
                            .put("shiftRoleString", targetShiftRoleString)
                            .put("eventSlackChannel", eventSlackChannel)
                            .put("volunteerName", volunteerName)
                            .put("eventName", eventName)
                            .put("rolesJsonString", webhook.data.getString("rolesJsonString"))
                            .put("confirmAssignedEmailTemplateId", webhook.data.getString("confirmAssignedEmailTemplateId"))
                            .put("confirmCancelledEmailTemplateId", webhook.data.getString("confirmCancelledEmailTemplateId"))
                )
                services.db.insertWebhook(cancelHook)
                services.server.mountWebhook(cancelHook)


                def emailTemplate = slurpDocument(services.googleAPI, webhook.data.getString("confirmAssignedEmailTemplateId"))
                def emailContents = emailTemplate.replaceAll("%EVENT_NAME%", eventName)
                emailContents = emailContents.replaceAll("%ROLE_NAME%", shiftRole.role.name)
                emailContents = emailContents.replaceAll("%SHIFT_INDEX%", Integer.toString(shiftRole.shift.index))
                emailContents = emailContents.replaceAll("%SHIFT_START%", shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter))
                emailContents = emailContents.replaceAll("%SHIFT_END%", shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter))
                emailContents = emailContents.replaceAll("%EVENT_DATE%", eventStartTime.format(eventDayFormatter))
                emailContents = emailContents.replaceAll("%CANCEL_LINK%", "<a href=\"http://${config.getString("host")}:${config.getInteger("port").toString()}/${cancelHook.path()}\">Cancel</a>")

                log.info "email contents: \n${emailContents}"

                sendEmail(services.googleAPI, "AutoWiSE", volunteerEmail, "[WiSER] Volunteer Sign-up Confirmation for ${eventName}", emailContents)

                //Notify slack
                sendSlackMessage(services.slackAPI, eventSlackChannel, "${volunteerName} has expressed interest in volunteering for ${eventName} as ${shiftRole.role.name}. They have been successfully assigned shift ${shiftRole.shift.index} starting at ${shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter)} and ending at ${shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter)}. "  )


                //TODO - remove this webhook from server
                return
            }
            log.info "Shift-role ${rowData.get(0)} has been filled!"
        }

    //If we make it to here, there are no available slots for the target shift role.
    //TODO: implement waitlist functionality.
    updateVolunteerStatus(services.googleAPI, eventSheetId, volunteerEmail, "Waitlisted", targetShiftRoleString)

    def emailTemplate = slurpDocument(services.googleAPI, webhook.data.getString("confirmWaitlistEmailTemplateId"))
    def emailContents = emailTemplate.replaceAll("%EVENT_NAME%", eventName)
    emailContents = emailContents.replaceAll("%ROLE_NAME%", shiftRole.role.name)
    emailContents = emailContents.replaceAll("%SHIFT_START%", shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter))
    emailContents = emailContents.replaceAll("%SHIFT_END%", shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter))

    sendEmail(services.googleAPI, "AutoWiSE", volunteerEmail, "[WiSER] Volunteer Sign-up Waitlist Confirmation for ${eventName}", emailContents)

    sendSlackMessage(services.slackAPI, eventSlackChannel, "${volunteerName} has expressed interest in volunteering for ${eventName} as ${shiftRole.role.name}. However there were no matching free slots for this volunteer, so they have been waitlisted and notified that they will be contacted if a slot frees up.")


}

