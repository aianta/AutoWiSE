package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.WaitlistEntry
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import java.time.ZonedDateTime

import static ca.ualberta.autowise.JsonUtils.slurpRolesJson
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.*
import static ca.ualberta.autowise.scripts.ManageEventStatusTable.*
import static ca.ualberta.autowise.scripts.google.GetSheetValue.*
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.*
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.*
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.*
import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.*
import static ca.ualberta.autowise.scripts.google.SendEmail.*
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.*

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.CancelShiftRole.class)

/**
 * @Author Alexandru Ianta
 * When a volunteer cancels on their commitment to volunteer for a shift role:
 * 1. Find them in the event status table and remove their email and name from the list.
 * 2. Update the volunteers status in the volunteer contact status sheet.
 * 2. Read through the volunteer contact status table and find the next waitlisted volunteer for the shift-role.
 *      a) If one exists, move them into the appropriate volunteer slot on the event status sheet.
 *      b) Create a cancel webhook for the waitlisted volunteer
 *      c) Email the replacement volunteer to let them know they have been put into the volunteer slot.
 *      d) Update the replacement volunteer's status in the volunteer contact status sheet.
 * 3. Email the volunteer that cancelled to confirm their cancellation.
 * 4. Notify slack of the changes.
 * 5. Mark the webhook as invoked
 */

static def cancelShiftRole(services, Webhook webhook, config){
    def volunteers = slurpVolunteerList(services.googleAPI, config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range"))
    def targetShiftRoleString = webhook.data.getString("shiftRoleString")
    def eventSheetId = webhook.data.getString("eventSheetId")
    def eventSlackChannel = webhook.data.getString("eventSlackChannel")
    def volunteerEmail = webhook.data.getString("volunteerEmail")
    def volunteerName = webhook.data.getString("volunteerName")
    def eventName = webhook.data.getString("eventName")
    List<Role> roles = slurpRolesJson(webhook.data.getString("rolesJsonString"))
    ShiftRole shiftRole = getShiftRole(targetShiftRoleString, roles)
    def eventStartTime = ZonedDateTime.parse(webhook.data.getString("eventStartTime"), EventSlurper.eventTimeFormatter)

    def data = getValuesAt(services.googleAPI, eventSheetId, FindAvailableShiftRoles.EVENT_STATUS_RANGE)

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
        if(!rowData.isEmpty() && rowData.size() >= 2 && rowData.get(1).equals(volunteerEmail)){

            def slackMessage = "${volunteerName} has cancelled their ${shiftRole.role.name} shift for ${eventName}."

            //Mark the volunteer as cancelled
            updateVolunteerStatus(services.googleAPI, eventSheetId, volunteerEmail, "Cancelled", targetShiftRoleString)

            //Get the waitlist for this shiftrole
            List<WaitlistEntry> waitlist = getWaitlistForShiftRole(services.googleAPI, eventSheetId, targetShiftRoleString)

            //If there are volunteers waiting to be put into this shift-role
            if (!waitlist.isEmpty()){ //Grab the first one, and enter their info into the event status sheet.
                def replacementEmail = waitlist.get(0).email
                def replacementName = getVolunteerByEmail(replacementEmail, volunteers).name

                rowData.set(1, replacementEmail)
                if(rowData.size() >=3){
                    rowData.set(2, replacementName)
                }else{
                    rowData.add(replacementName)
                }


                //Create a cancel hook for the replacement volunteer
                Webhook cancelHook = new Webhook(
                        id: UUID.randomUUID(),
                        eventId: webhook.eventId,
                        type: HookType.CANCEL_ROLE_SHIFT,
                        expiry: eventStartTime.toInstant().toEpochMilli(),
                        invoked: false,
                        data: new JsonObject()
                                .put("volunteerEmail", replacementEmail)
                                .put("volunteerName", replacementName)
                                .put("eventSheetId", eventSheetId)
                                .put("eventStartTime", eventStartTime.format(EventSlurper.eventTimeFormatter))
                                .put("shiftRoleString", targetShiftRoleString)
                                .put("eventSlackChannel", eventSlackChannel)
                                .put("eventName", eventName)
                                .put("rolesJsonString", webhook.data.getString("rolesJsonString"))
                                .put("confirmAssignedEmailTemplateId", webhook.data.getString("confirmAssignedEmailTemplateId"))
                                .put("confirmCancelledEmailTemplateId", webhook.data.getString("confirmCancelledEmailTemplateId"))

                )
                services.db.insertWebhook(cancelHook)
                services.server.mountWebhook(cancelHook)

                def emailTemplate = slurpDocument(services.googleAPI, webhook.data.getString("confirmAssignedEmailTemplateId"))
                def emailContent = emailTemplate.replaceAll("%EVENT_NAME%", eventName)
                emailContent = emailContent.replaceAll("%ROLE_NAME%", shiftRole.role.name )
                emailContent = emailContent.replaceAll("%SHIFT_INDEX%", Integer.toString(shiftRole.shift.index))
                emailContent = emailContent.replaceAll("%SHIFT_START%", shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter))
                emailContent = emailContent.replaceAll("%SHIFT_END%", shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter))
                emailContent = emailContent.replaceAll("%EVENT_DATE%", eventStartTime.format(SignupForRoleShift.eventDayFormatter))
                emailContent = emailContent.replaceAll("%CANCEL_LINK%", "<a href=\"http://localhost:8080/${cancelHook.path()}\">Cancel</a>")

                sendEmail(services.googleAPI, "AutoWiSE", replacementEmail, "[WiSER] Moved off waitlist, assigned volunteer role for ${eventName}",emailContent)

                updateVolunteerStatus(services.googleAPI, eventSheetId, replacementEmail, "Accepted", targetShiftRoleString )
                slackMessage = slackMessage + " ${replacementName} has automatically been moved from the waitlist into the freed slot and notified of their role assignment."

            }else{
                //Clear this volunteer from the event status sheet
                rowData.set(1, "")
                if(rowData.size() >= 3){
                    rowData.set(2, "")
                }else{
                    rowData.add("")
                }
            }
            updateEventStatusTable(services.googleAPI,eventSheetId, data)

            //Email the volunteer who cancelled
            def emailTemplate = slurpDocument(services.googleAPI, webhook.data.getString("confirmCancelledEmailTemplateId"))
            sendEmail(services.googleAPI, "AutoWiSE", volunteerEmail, "[WiSER] Volunteering Cancellation Confirmation for ${eventName}", emailTemplate)

            sendSlackMessage(services.slackAPI, eventSlackChannel, slackMessage)

            services.db.markWebhookInvoked(webhook.id)
        }
    }

}


