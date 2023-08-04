package ca.ualberta.autowise.scripts.tasks

import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.MassEmailEntry
import ca.ualberta.autowise.model.MassEmailSender
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

import org.slf4j.LoggerFactory

import java.time.ZonedDateTime

import static ca.ualberta.autowise.JsonUtils.slurpRolesJson
import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.getShiftRole
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.syncEventVolunteerContactSheet
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.getVolunteerByEmail
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.slurpVolunteerList
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage

@Field static def log = LoggerFactory.getLogger(ConfirmationEmailTask.class)

static def confirmationEmailTask(Vertx vertx, services, Task task, config, subject){

    def eventName = task.data.getString("eventName")
    def eventSheetId = task.data.getString("eventSheetId")
    def eventbriteLink = task.data.getString("eventbriteLink")
    def eventSlackChannel  = task.data.getString("eventSlackChannel")
    List<Role> eventRoles = slurpRolesJson(task.data.getString("rolesJsonString"))
    def eventStartTime = ZonedDateTime.parse(task.data.getString("eventStartTime"), EventSlurper.eventTimeFormatter)

    // Fetch all the data we'll need to execute the task
    return slurpVolunteerList(services.googleAPI, config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range"))
        .compose{
            volunteers->
                return CompositeFuture.all(
                        syncEventVolunteerContactSheet(services.googleAPI, eventSheetId, volunteers),
                        slurpDocument(services.googleAPI, task.data.getString("emailTemplateId"))

                ).compose{
                    composite->
                        def volunteerContactStatusData = composite.resultAt(0)
                        def emailTemplate = composite.resultAt(1)

                        Promise promise = Promise.promise();
                        MassEmailSender sender = new MassEmailSender(vertx, services, config, volunteerContactStatusData)

                        sender.sendMassEmail((rowData, rowFuture)->{
                            if(rowData.get(0).toLowerCase().equals("Volunteers".toLowerCase())){ //Ignore case, just in case...
                                return null //Return a null email entry to skip this row as it is the header row
                            }

                            if(rowData.get(2).equals("Accepted")){
                                def volunteer = getVolunteerByEmail(rowData.get(0), volunteers)
                                if(volunteer == null){
                                    log.warn "Volunteer with email ${rowData.get(0)} does not appear in WiSER volunteer pool, skipping confirmation email"
                                    sendSlackMessage(services.slackAPI, eventSlackChannel, "${rowData.get(0)} appears on the ${eventName}' volunteer contact status' sheet but does not appear in the WiSER general volunteer list. No email will be sent to ${rowData.get(0)}.")
                                    return null //Return a null email entry to skip this row as we don't recognize the volunteer
                                }

                                //Fetch the relevant shift role
                                ShiftRole shiftRole = getShiftRole(rowData.get(7), eventRoles)

                                //Make the confirmation webhook
                                Webhook confirmHook = new Webhook(
                                        id: UUID.randomUUID(),
                                        eventId: task.eventId,
                                        type: HookType.CONFIRM_ROLE_SHIFT,
                                        expiry: eventStartTime.toInstant().toEpochMilli(),
                                        invoked: false,
                                        data: new JsonObject()
                                                .put("volunteerName", volunteer.name)
                                                .put("volunteerEmail", volunteer.email)
                                                .put("eventSheetId", eventSheetId)
                                                .put("shiftRoleString", rowData.get(7))
                                )
                                services.db.insertWebhook(confirmHook)
                                services.server.mountWebhook(confirmHook)

                                //Make the cancellation webhook
                                Webhook cancelHook = new Webhook(
                                        id: UUID.randomUUID(),
                                        eventId: task.eventId,
                                        type: HookType.CANCEL_ROLE_SHIFT,
                                        expiry: eventStartTime.toInstant().toEpochMilli(),
                                        invoked: false,
                                        data: new JsonObject()
                                                .put("volunteerEmail", volunteer.email)
                                                .put("eventSheetId", eventSheetId)
                                                .put("eventStartTime", eventStartTime.format(EventSlurper.eventTimeFormatter))
                                                .put("shiftRoleString", rowData.get(7))
                                                .put("eventSlackChannel", eventSlackChannel)
                                                .put("volunteerName", volunteer.name)
                                                .put("eventName", eventName)
                                                .put("rolesJsonString", task.data.getString("rolesJsonString"))
                                                .put("confirmAssignedEmailTemplateId", task.data.getString("confirmAssignedEmailTemplateId"))
                                                .put("confirmCancelledEmailTemplateId", task.data.getString("confirmCancelledEmailTemplateId"))
                                )
                                services.db.insertWebhook(cancelHook)
                                services.server.mountWebhook(cancelHook)

                                def emailContents = emailTemplate.replaceAll("%VOLUNTEER_NAME%", volunteer.name)
                                emailContents = emailContents.replaceAll("%ROLE%", shiftRole.role.name)
                                emailContents = emailContents.replaceAll("%SHIFT_START%", shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter))
                                emailContents = emailContents.replaceAll("%SHIFT_END%", shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter))
                                emailContents = emailContents.replaceAll("%CONFIRMATION_LINK%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}/${confirmHook.path()}\">Confirmation Link</a>")
                                emailContents = emailContents.replaceAll("%EVENTBRITE_LINK%", "<a href=\"${eventbriteLink}\">eventbrite</a>")
                                emailContents = emailContents.replaceAll("%CANCEL_LINK%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}/${cancelHook.path()}\">Cancellation Link</a>")

                                return new MassEmailEntry(
                                        target: volunteer.email,
                                        content: emailContents,
                                        subject: subject
                                )
                            }
                        }, complete->{
                            complete.onSuccess{
                                log.info "Successfully send confirmation emails for ${eventName} taskId: ${task.taskId.toString()}"
                                services.db.markTaskComplete(task.taskId)
                                if (task.notify){
                                    sendSlackMessage(services.slackAPI, task.data.getString("eventSlackChannel"), "Confirmation emails sent to all volunteers who've accepted shift roles for ${eventName}")
                                        .onSuccess{
                                            promise.tryComplete()
                                        }
                                        .onFailure{
                                            err->log.error err.getMessage(), err
                                                promise.fail(err)
                                        }
                                }
                                promise.tryComplete()
                            }.onFailure{
                                err->
                                    log.error "Error sending confirmation emails to volunteers who've accepted shift roles for ${eventName}. TaskId: ${task.taskId.toString()}"
                                    log.error err.getMessage(), err
                                    sendSlackMessage(services.slackAPI, eventSlackChannel,"Error sending confirmation emails to volunteers who've accepted shift roles for ${eventName}. TaskId: ${task.taskId.toString()}" )
                                    promise.fail(err)
                            }

                        })
                }

        }
}