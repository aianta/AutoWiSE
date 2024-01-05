package ca.ualberta.autowise.scripts.tasks

import ca.ualberta.autowise.model.ContactStatus
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.MassEmailEntry
import ca.ualberta.autowise.model.MassEmailSender
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import java.time.ZonedDateTime
import java.util.function.Predicate
import java.util.stream.Collectors

import static ca.ualberta.autowise.utils.JsonUtils.slurpRolesJson
import static ca.ualberta.autowise.scripts.BuildShiftRoleOptions.buildShiftRoleOptions
import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.findAvailableShiftRoles
import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.getShiftRole
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.syncEventVolunteerContactSheet
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.getVolunteerByEmail
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.slurpVolunteerList
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.updateVolunteerStatus

@Field static def log = LoggerFactory.getLogger(RecruitmentEmailTask.class)


/**
 * @author Alexandru Ianta
 * WARNING: this documentation may be out of date as of July 26, 2023
 * The following task is broken down as follows:
 *
 * 1. Fetch the volunteer pool and fetch the email template
 *      a. Fetch the 'Volunteer Contact Status' table
 *      b. Compare the volunteer pool to the list on the 'Volunteer Contact Status' sheet. Append volunteers that don't appear in the sheet.
 * 2. Assemble the Shift-Role volunteer options summary for the event.
 * 3. One-by-one for each Volunteer marked 'Not Contacted': generate unique webhooks for each volunteer and send them the recruitment email.
 *    a. After the email is sent, update the 'Volunteer Contact Status' sheet with status 'Waiting for response' and the 'Last Contacted' value
 *    b. Pick one random volunteer whose email will be BCC'd to volunteer coordinators for quality control.
 * 4. Mark our task complete in SQLite
 * 5. If the task has the notify flag turned on, notify slack that the task has been completed.
 *
 */

static def recruitmentEmailTask(Vertx vertx, services, Task task, Event event, config, Predicate<String> statusPredicate, subject){
    log.info "Executing recruitment email task!"
    // Fetch all the data we'll need to execute the task
    return slurpVolunteerList(services.googleAPI, config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range"))
        .compose {
            volunteers->

                log.info "got volunteer list"


                return CompositeFuture.all(
                        syncEventVolunteerContactSheet(services.db,task.eventId, event.sheetId, volunteers), //Sync the list of volunteers contacted for this event with the global list of wiser volunteers
                        slurpDocument(services.googleAPI, task.data.getString("emailTemplateId")),
                        services.db.findAvailableShiftRoles(event.sheetId)
                ).onFailure{
                    err->Future.failedFuture(err)
                }
                        .compose{
                    composite->
                        List<ContactStatus> volunteerContactStatusData = composite.resultAt(0)
                        def emailTemplate = composite.resultAt(1)
                        def unfilledShiftRoles = composite.resultAt(2)

                        log.info "Synced volunteer contact status sheet, slurped email template, and got unfilled shift roles!"

                        if(unfilledShiftRoles.size() == 0){
                            return handleNoAvailableShiftRoles(services, task, event)
                        }

                        log.info "Unfilled shift roles exist for ${event.name}"

                        Promise promise = Promise.promise()
                        MassEmailSender sender = new MassEmailSender(vertx, services, config, volunteerContactStatusData)

                        sender.sendMassEmail((contactStatus, rowFuture)->{


                            if(statusPredicate.test(contactStatus.status)){
                                def volunteer = getVolunteerByEmail(contactStatus.volunteerEmail, volunteers)
                                if(volunteer == null){
                                    log.warn "Volunteer with email ${contactStatus.volunteerEmail} does not appear in WiSER volunteer pool, skipping recruitment email"
                                    sendSlackMessage(services.slackAPI, event.eventSlackChannel, "${contactStatus.volunteerEmail} appears on the ${event.name}' volunteer contact status' sheet but does not appear in the WiSER general volunteer list. No email will be sent to ${contactStatus.volunteerEmail}.")
                                    return null //Return a null email entry to skip this row as we don't recognize the volunteer
                                }

                                rowFuture.onSuccess{
                                    log.info "updating ${task.eventId} - ${event.sheetId} - ${volunteer.email} - \"Waiting for response\" - null"
                                    updateVolunteerStatus(services.db, task.eventId,  event.sheetId, volunteer.email, "Waiting for response", null)
                                        .onSuccess{
                                            log.info "volunteer contact status updated"
                                        }
                                        .onFailure{
                                            log.error "Error updating volunteer contact status"
                                        }
                                }

                                def emailContents = makeShiftRoleEmail(services, config, volunteer, task, unfilledShiftRoles, event, emailTemplate)
                                return new MassEmailEntry(target: volunteer.email, content: emailContents, subject: subject)


                            }

                        }, taskComplete->{
                            taskComplete.onSuccess{
                                log.info "Sent all recruitment emails for ${event.name} completing task ${task.taskId.toString()}"
                                services.db.markTaskComplete(task.taskId)



                                sendSlackMessage(services.slackAPI, event.eventSlackChannel, "Sent recruitment emails for ${event.name} successfully!")
                                    .onSuccess{
                                        promise.tryComplete()
                                    }
                                    .onFailure{
                                        err->log.error err.getMessage(), err
                                            promise.fail(err)
                                    }


                                promise.tryComplete()


                            }.onFailure{err->
                                log.info "Error sending recruitment emails for ${event.name}, task ${task.taskId.toString()}"
                                log.error err.getMessage(), err
                                sendSlackMessage(services.slackAPI, event.eventSlackChannel, "Error while sending recruitment emails for ${event.name}.  TaskId: ${task.taskId.toString()}. Error Message: ${err.getMessage()}")
                                promise.fail(err)
                            }

                        })

                        return promise.future()
                }


        }



}


private static String makeShiftRoleEmail(services, config, Volunteer volunteer, Task task, Set<String> unfilledShiftRoles, Event event, emailTemplate){
    List<ShiftRole> availableShiftRoles = unfilledShiftRoles.stream()
            .map(shiftRoleString->getShiftRole(shiftRoleString, event.roles))
            .map(shiftRole->{
                // Generate a personalized webhook for this shiftRole
                Webhook volunteerWebhook = new Webhook(
                        id: UUID.randomUUID(),
                        eventId: task.eventId,
                        type: HookType.ACCEPT_ROLE_SHIFT,
                        expiry: event.startTime.toInstant().toEpochMilli(),
                        invoked: false,
                        data: new JsonObject()
                                .put("volunteerName", volunteer.name)
                                .put("volunteerEmail", volunteer.email)
                                .put("shiftRoleString", shiftRole.shiftRoleString)
                )
                services.db.insertWebhook(volunteerWebhook)
                services.server.mountWebhook(volunteerWebhook)
                shiftRole.acceptHook = volunteerWebhook
                return shiftRole
            })
            .collect(Collectors.toList())

    //Create reject webhook
    Webhook rejectHook = new Webhook(
            id: UUID.randomUUID(),
            eventId: task.eventId,
            type: HookType.REJECT_VOLUNTEERING_FOR_EVENT,
            expiry: event.startTime.toInstant().toEpochMilli(),
            invoked: false,
            data: new JsonObject()
                    .put("volunteerName", volunteer.name)
                    .put("volunteerEmail", volunteer.email)
                    .put("emailTemplateId",task.data.getString("confirmRejectedEmailTemplatedId") )

    )
    services.db.insertWebhook(rejectHook)
    services.server.mountWebhook(rejectHook)

    def shiftRoleHTMLTable = buildShiftRoleOptions(availableShiftRoles, config)
    def emailContents = emailTemplate.replaceAll("%AVAILABLE_SHIFT_ROLES%", shiftRoleHTMLTable)
    emailContents = emailContents.replaceAll("%EVENT_NAME%", event.name)
    emailContents = emailContents.replaceAll("%EVENT_DESCRIPTION%", event.description)
    emailContents = emailContents.replaceAll("%REJECT_LINK%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}/${rejectHook.path()}\">Click me if you aren't able to volunteer for this event.</a>")
    emailContents = emailContents.replaceAll("%EVENTBRITE_LINK%", "<a href=\"${event.eventbriteLink}\">eventbrite</a>")

    return emailContents
}

private static def handleNoAvailableShiftRoles(services, Task task, Event event){
    services.db.markTaskComplete(task.taskId)
    return sendSlackMessage(services.slackAPI, event.eventSlackChannel, "No unfilled shifts for ${event.name}. Aborting recruitment email task.")
}