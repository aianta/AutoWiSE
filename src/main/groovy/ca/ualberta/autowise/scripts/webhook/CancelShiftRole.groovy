package ca.ualberta.autowise.scripts.webhook


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

static def cancelShiftRole(services, Webhook webhook, config){
    def volunteerEmail = webhook.data.getString("volunteerEmail")
    def eventSheetId = webhook.data.getString("eventSheetId")

    return hasVolunteerAlreadyCancelled(services.googleAPI, eventSheetId, volunteerEmail).compose{
        alreadyCancelled->

            if(alreadyCancelled){
                log.info "${volunteerEmail} has already cancelled before halting cancel operation."
                return Future.failedFuture("You have already cancelled on a shift-role for this event.")
            }


            return slurpVolunteerList(services.googleAPI, config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range"))
                .compose{
                    volunteers->
                        def targetShiftRoleString = webhook.data.getString("shiftRoleString")
                        def eventSlackChannel = webhook.data.getString("eventSlackChannel")
                        def volunteerName = webhook.data.getString("volunteerName")
                        def eventName = webhook.data.getString("eventName")
                        List<Role> roles = slurpRolesJson(webhook.data.getString("rolesJsonString"))
                        ShiftRole shiftRole = getShiftRole(targetShiftRoleString, roles)
                        def eventStartTime = ZonedDateTime.parse(webhook.data.getString("eventStartTime"), EventSlurper.eventTimeFormatter)

                        return getValuesAt(services.googleAPI, eventSheetId, FindAvailableShiftRoles.EVENT_STATUS_RANGE).compose{
                            data ->

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
                                        return updateVolunteerStatus(services.googleAPI, eventSheetId, volunteerEmail, "Cancelled", targetShiftRoleString)
                                                .compose {
                                                    //Get the waitlist for this shiftrole
                                                    getWaitlistForShiftRole(services.googleAPI, eventSheetId, targetShiftRoleString).compose {
                                                        List<WaitlistEntry> waitlist ->

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

                                                                return CompositeFuture.all(
                                                                        updateEventStatusTable(services.googleAPI,eventSheetId, data), //Update the event status table with the replacements info.
                                                                        slurpDocument(services.googleAPI, webhook.data.getString("confirmAssignedEmailTemplateId")), //Get the email template to send to the waitlisted volunteer
                                                                        slurpDocument(services.googleAPI, webhook.data.getString("confirmCancelledEmailTemplateId")) //Get the email template to send to the cancelling volunteer
                                                                ).compose{
                                                                    compositeResult->
                                                                        def confirmAssignmentEmailTemplate = compositeResult.resultAt(0)
                                                                        def confirmCancelEmailTemplate = compositeResult.resultAt(1)

                                                                        def emailContent = confirmAssignmentEmailTemplate.replaceAll("%EVENT_NAME%", eventName)
                                                                        emailContent = emailContent.replaceAll("%ROLE_NAME%", shiftRole.role.name )
                                                                        emailContent = emailContent.replaceAll("%SHIFT_INDEX%", Integer.toString(shiftRole.shift.index))
                                                                        emailContent = emailContent.replaceAll("%SHIFT_START%", shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter))
                                                                        emailContent = emailContent.replaceAll("%SHIFT_END%", shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter))
                                                                        emailContent = emailContent.replaceAll("%EVENT_DATE%", eventStartTime.format(SignupForRoleShift.eventDayFormatter))
                                                                        emailContent = emailContent.replaceAll("%CANCEL_LINK%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}/${cancelHook.path()}\">Cancel</a>")

                                                                        slackMessage = slackMessage + " ${replacementName} has automatically been moved from the waitlist into the freed slot and notified of their role assignment."

                                                                        return CompositeFuture.all(
                                                                                sendEmail(config, services.googleAPI, config.getString("sender_email"), replacementEmail, "[WiSER] Moved off waitlist, assigned volunteer role for ${eventName}",emailContent),
                                                                                updateVolunteerStatus(services.googleAPI, eventSheetId, replacementEmail, "Accepted", targetShiftRoleString ),
                                                                                sendSlackMessage(services.slackAPI, eventSlackChannel, slackMessage),
                                                                                sendEmail(config, services.googleAPI, config.getString("sender_email"), volunteerEmail,"[WiSER] Volunteering Cancellation Confirmation for ${eventName}", confirmCancelEmailTemplate)
                                                                        )

                                                                }
                                                            }else{
                                                                //If there is no one on the waitlist for this shift role just clear this volunteer from the event status sheet
                                                                rowData.set(1, "")
                                                                if(rowData.size() >= 3){
                                                                    rowData.set(2, "")
                                                                }else{
                                                                    rowData.add("")
                                                                }
                                                            }

                                                            return CompositeFuture.all(
                                                                    updateEventStatusTable(services.googleAPI,eventSheetId, data),
                                                                    slurpDocument(services.googleAPI, webhook.data.getString("confirmCancelledEmailTemplateId"))
                                                            ).compose {
                                                                compositeResult->
                                                                    def emailTemplate = compositeResult.resultAt(1)

                                                                    return CompositeFuture.all(
                                                                            sendEmail(config, services.googleAPI, config.getString("sender_email"), volunteerEmail, "[WiSER] Volunteering Cancellation Confirmation for ${eventName}", emailTemplate),
                                                                            sendSlackMessage(services.slackAPI, eventSlackChannel, slackMessage)
                                                                    )
                                                            }

                                                    }




                                                }
                                                .onFailure{
                                                    err->
                                                        log.error "Error updating volunteer status to cancelled."
                                                        log.error err.getMessage(), err
                                                        return Future.failedFuture(err)

                                                }
                                    }


                                }

                                return Future.failedFuture("Could not find event status entry for ${volunteerName} [${volunteerEmail}]!")

                        }
                }

    }

}


