package ca.ualberta.autowise.scripts.tasks


import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.MassEmailEntry
import ca.ualberta.autowise.model.MassEmailSender
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import java.time.ZonedDateTime
import java.util.function.Predicate
import java.util.stream.Collectors

import static ca.ualberta.autowise.JsonUtils.slurpRolesJson
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

static def recruitmentEmailTask(Vertx vertx, services, Task task, config, Predicate<String> statusPredicate, subject){
    // Fetch all the data we'll need to execute the task
    def volunteers = slurpVolunteerList(services.googleAPI, config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range"))
    def eventName = task.data.getString("eventName")
    def eventSheetId = task.data.getString("eventSheetId")
    def eventbriteLink = task.data.getString("eventbriteLink")
    def eventSlackChannel  = task.data.getString("eventSlackChannel")
    List<Role> eventRoles = slurpRolesJson(task.data.getString("rolesJsonString"))
    def eventStartTime = ZonedDateTime.parse(task.data.getString("eventStartTime"), EventSlurper.eventTimeFormatter)
    def volunteerContactStatusData = syncEventVolunteerContactSheet(services.googleAPI, eventSheetId, volunteers)
    def emailTemplate = slurpDocument(services.googleAPI, task.data.getString("emailTemplateId"))
    def unfilledShiftRoles = findAvailableShiftRoles(services.googleAPI, eventSheetId)

    if(unfilledShiftRoles == 0){
        handleNoAvailableShiftRoles(services, task, eventName)
    }

    MassEmailSender sender = new MassEmailSender(vertx, services, config, volunteerContactStatusData)

    sender.sendMassEmail((rowData, rowFuture)->{
        if(rowData.get(0).toLowerCase().equals("Volunteers".toLowerCase())){ //Ignore case, just in case...
            return null //Return a null email entry to skip this row as it is the header row
        }

        if(statusPredicate.test(rowData.get(2))){
            def volunteer = getVolunteerByEmail(rowData.get(0), volunteers)
            if(volunteer == null){
                log.warn "Volunteer with email ${rowData.get(0)} does not appear in WiSER volunteer pool, skipping recruitment email"
                sendSlackMessage(services.slackAPI, eventSlackChannel, "${rowData.get(0)} appears on the ${eventName}' volunteer contact status' sheet but does not appear in the WiSER general volunteer list. No email will be sent to ${rowData.get(0)}.")
                return null //Return a null email entry to skip this row as we don't recognize the volunteer
            }

            rowFuture.onSuccess{
                updateVolunteerStatus(services.googleAPI, eventSheetId, volunteer.email, "Waiting for response", null)
            }

            def emailContents = makeShiftRoleEmail(services, config, volunteer, task, unfilledShiftRoles, eventRoles, eventStartTime, eventName, eventSheetId, eventSlackChannel, eventbriteLink, emailTemplate)
            return new MassEmailEntry(target: volunteer.email, content: emailContents, subject: subject)
        }

    }, taskComplete->{
        taskComplete.onSuccess{
            log.info "Sent all recruitment emails for ${eventName} completing task ${task.taskId.toString()}"
            services.db.markTaskComplete(task.taskId)

            if(task.notify){
                sendSlackMessage(services.slackAPI, eventSlackChannel, "Sent recruitment emails for ${eventName} successfully!")
            }
        }.onFailure{err->
            log.info "Error sending recruitment emails for ${eventName}, task ${task.taskId.toString()}"
            log.error err.getMessage(), err
            sendSlackMessage(services.slackAPI, eventSlackChannel, "Error while sending recruitment emails for ${eventName}.  TaskId: ${task.taskId.toString()}. Error Message: ${err.getMessage()}")
        }

    })
}


private static String makeShiftRoleEmail(services, config, Volunteer volunteer, Task task, Set<String> unfilledShiftRoles, List<Role> eventRoles, eventStartTime, eventName, eventSheetId, eventSlackChannel, eventbriteLink, emailTemplate){
    List<ShiftRole> availableShiftRoles = unfilledShiftRoles.stream()
            .map(shiftRoleString->getShiftRole(shiftRoleString, eventRoles))
            .map(shiftRole->{
                // Generate a personalized webhook for this shiftRole
                Webhook volunteerWebhook = new Webhook(
                        id: UUID.randomUUID(),
                        eventId: task.eventId,
                        type: HookType.ACCEPT_ROLE_SHIFT,
                        expiry: eventStartTime.toInstant().toEpochMilli(),
                        invoked: false,
                        data: new JsonObject()
                                .put("volunteerName", volunteer.name)
                                .put("volunteerEmail", volunteer.email)
                                .put("eventName", eventName)
                                .put("eventSheetId", eventSheetId)
                                .put("shiftRoleString", shiftRole.shiftRoleString)
                                .put("confirmAssignedEmailTemplateId", task.data.getString("confirmAssignedEmailTemplateId"))
                                .put("confirmWaitlistEmailTemplateId", task.data.getString("confirmWaitlistEmailTemplateId"))
                                .put("confirmCancelledEmailTemplateId", task.data.getString("confirmCancelledEmailTemplateId"))
                                .put("eventSlackChannel", eventSlackChannel)
                                .put("rolesJsonString", task.data.getString("rolesJsonString"))
                                .put("eventStartTime", eventStartTime.format(EventSlurper.eventTimeFormatter))
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
            expiry: eventStartTime.toInstant().toEpochMilli(),
            invoked: false,
            data: new JsonObject()
                    .put("volunteerName", volunteer.name)
                    .put("volunteerEmail", volunteer.email)
                    .put("eventSheetId", eventSheetId)
                    .put("eventName", eventName)
                    .put("eventSlackChannel", eventSlackChannel)
                    .put("emailTemplateId",task.data.getString("confirmRejectedEmailTemplatedId") )

    )
    services.db.insertWebhook(rejectHook)
    services.server.mountWebhook(rejectHook)

    def shiftRoleHTMLTable = buildShiftRoleOptions(availableShiftRoles, config)
    def emailContents = emailTemplate.replaceAll("%AVAILABLE_SHIFT_ROLES%", shiftRoleHTMLTable)
    emailContents = emailContents.replaceAll("%REJECT_LINK%", "<a href=\"http://${config.getString("host")}:${config.getInteger("port").toString()}/${rejectHook.path()}\">Click me if you aren't able to volunteer for this event.</a>")
    emailContents = emailContents.replaceAll("%EVENTBRITE_LINK%", "<a href=\"${eventbriteLink}\">eventbrite</a>")

    return emailContents
}

private static def handleNoAvailableShiftRoles(services, Task task, eventName){
    services.db.markTaskComplete(task.taskId)
    if(task.notify){
        sendSlackMessage(services.slackAPI, task.data.getString("eventSlackChannel"), "No unfilled shifts for ${eventName}. Aborting recruitment email task.")
    }
    return
}