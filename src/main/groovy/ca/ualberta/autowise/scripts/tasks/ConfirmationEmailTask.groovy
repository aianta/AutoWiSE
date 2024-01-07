package ca.ualberta.autowise.scripts.tasks

import ca.ualberta.autowise.model.ContactStatus
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.MassEmailEntry
import ca.ualberta.autowise.model.MassEmailSender
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import ca.ualberta.autowise.scripts.webhook.SignupForRoleShift
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.utils.ShiftRoleUtils.getShiftRole
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.syncEventVolunteerContactSheet
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.getVolunteerByEmail
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.slurpVolunteerList
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage

@Field static def log = LoggerFactory.getLogger(ConfirmationEmailTask.class)

static def confirmationEmailTask(Vertx vertx, services, Task task, Event event, config, subject){


    // Fetch all the data we'll need to execute the task
    return slurpVolunteerList(services.googleAPI, config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range"))
        .compose{
            volunteers->
                return CompositeFuture.all(
                        syncEventVolunteerContactSheet(services.db, task.eventId, event.sheetId, volunteers),
                        slurpDocument(services.googleAPI, task.data.getString("emailTemplateId"))
                ).compose{
                    composite->
                        List<ContactStatus> volunteerContactStatusData = composite.resultAt(0)
                        def emailTemplate = composite.resultAt(1)


                        volunteerContactStatusData.forEach {log.info "Contact status: {} {} ", it.volunteerEmail, it.status}

                        Promise promise = Promise.promise();
                        MassEmailSender sender = new MassEmailSender(vertx, services, config, volunteerContactStatusData)

                        sender.sendMassEmail((contactStatus, rowFuture)->{
                            log.info "inside row function, processing contact status {} {} status.equals(Accepted)? {}", contactStatus.volunteerEmail, contactStatus.status, contactStatus.status.equals("Accepted")
                            if(contactStatus.status.equals("Accepted")){
                                try {

                                    def volunteer = getVolunteerByEmail(contactStatus.volunteerEmail, volunteers)
                                    if (volunteer == null) {
                                        log.warn "Volunteer with email ${contactStatus.volunteerEmail} does not appear in WiSER volunteer pool, skipping confirmation email"
                                        sendSlackMessage(services.slackAPI, event.eventSlackChannel, "${contactStatus.volunteerEmail} appears on the ${event.name}' volunteer contact status' sheet but does not appear in the WiSER general volunteer list. No email will be sent to ${contactStatus.volunteerEmail}.")
                                        return null
                                        //Return a null email entry to skip this row as we don't recognize the volunteer
                                    }


                                    //Make the confirmation webhook
                                    Webhook confirmHook = new Webhook(
                                            id: UUID.randomUUID(),
                                            eventId: task.eventId,
                                            type: HookType.CONFIRM_ROLE_SHIFT,
                                            expiry: event.startTime.toInstant().toEpochMilli(),
                                            invoked: false,
                                            data: new JsonObject()
                                                    .put("volunteerName", volunteer.name)
                                                    .put("volunteerEmail", volunteer.email)
                                                    .put("shiftRoleString", contactStatus.desiredShiftRole)
                                    )
                                    services.db.insertWebhook(confirmHook)
                                    services.server.mountWebhook(confirmHook)

                                    //Make the cancellation webhook
                                    Webhook cancelHook = new Webhook(
                                            id: UUID.randomUUID(),
                                            eventId: task.eventId,
                                            type: HookType.CANCEL_ROLE_SHIFT,
                                            expiry: event.startTime.toInstant().toEpochMilli(),
                                            invoked: false,
                                            data: new JsonObject()
                                                    .put("volunteerEmail", volunteer.email)
                                                    .put("shiftRoleString", contactStatus.desiredShiftRole)
                                                    .put("volunteerName", volunteer.name)


                                    )
                                    services.db.insertWebhook(cancelHook)
                                    services.server.mountWebhook(cancelHook)

                                    ShiftRole shiftRole = getShiftRole(contactStatus.desiredShiftRole, event.roles)

                                    def emailContents = makeConfirmEmail(emailTemplate, shiftRole, volunteer.name, event, cancelHook, confirmHook, config)

                                    log.info "Confirmation email content: \n {}", emailContents

                                    return new MassEmailEntry(
                                            target: volunteer.email,
                                            content: emailContents,
                                            subject: subject
                                    )

                                }catch (Exception e){
                                    log.error e.getMessage(), e
                                }
                            }

                            return null;
                        }, complete->{
                            complete.onSuccess{
                                log.info "Successfully send confirmation emails for ${event.name} taskId: ${task.taskId.toString()}"
                                services.db.markTaskComplete(task.taskId)

                                    sendSlackMessage(services.slackAPI, event.eventSlackChannel, "Confirmation emails sent to all volunteers who've accepted shift roles for ${event.name}")
                                        .onSuccess{
                                            promise.tryComplete()
                                        }
                                        .onFailure{
                                            err->log.error err.getMessage(), err
                                                promise.fail(err)
                                        }

                                promise.tryComplete()
                            }.onFailure{
                                err->
                                    log.error "Error sending confirmation emails to volunteers who've accepted shift roles for ${event.name}. TaskId: ${task.taskId.toString()}"
                                    log.error err.getMessage(), err
                                    sendSlackMessage(services.slackAPI, config.getString("technical_channel"),"Error sending confirmation emails to volunteers who've accepted shift roles for ${event.name} (${task.eventId.toString()}). TaskId: ${task.taskId.toString()}" )
                                    promise.fail(err)
                            }

                        })
                }

        }
}

static def makeConfirmEmail(template, ShiftRole shiftRole, String volunteerName, Event event, Webhook cancelHook, Webhook confirmHook, config){
    def emailContents = template.replaceAll("%VOLUNTEER_NAME%", volunteerName)
    emailContents = emailContents.replaceAll("%EVENT_NAME%", event.name)
    emailContents = emailContents.replaceAll("%EVENT_DATE%", event.startTime.format(SignupForRoleShift.eventDayFormatter))
    emailContents = emailContents.replaceAll("%ROLE%", shiftRole.role.name)
    emailContents = emailContents.replaceAll("%SHIFT_START%", shiftRole.shift.startTime.format(EventSlurper.shiftTimeFormatter))
    emailContents = emailContents.replaceAll("%SHIFT_END%", shiftRole.shift.endTime.format(EventSlurper.shiftTimeFormatter))
    emailContents = emailContents.replaceAll("%CONFIRMATION_LINK%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}/${confirmHook.path()}\">Confirmation Link</a>")
    emailContents = emailContents.replaceAll("%EVENTBRITE_LINK%", "<a href=\"${event.eventbriteLink}\">eventbrite</a>")
    emailContents = emailContents.replaceAll("%CANCEL_LINK%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}/${cancelHook.path()}\">Cancellation Link</a>")
    return emailContents
}