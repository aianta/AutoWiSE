package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
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
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.updateVolunteerContactStatusTable
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.SendEmail.sendEmail
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.getVolunteerByEmail
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.slurpVolunteerList
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.updateVolunteerStatus

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.RecruitmentEmailTask.class)

static def recruitmentEmailTask(Vertx vertx, services, Task task, config, Predicate<String> statusPredicate, resultingStatus, subject){
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

    Future lastFuture = null
    def it = volunteerContactStatusData.listIterator()
    while (it.hasNext()){
        def rowData = it.next()
        if(rowData.get(0).toLowerCase().equals("Volunteers".toLowerCase())){ //Ignore case, just in case...
            continue //Skip header row
        }

        if(statusPredicate.test(rowData.get(2))){
            def volunteer = getVolunteerByEmail(rowData.get(0), volunteers)
            if(volunteer == null){
                log.warn "Volunteer with email ${rowData.get(0)} does not appear in WiSER volunteer pool, skipping recruitment email"
                continue
            }


            def future = vertx.executeBlocking(blocking->{
                def emailContents = makeShiftRoleEmail(services, volunteer, task, unfilledShiftRoles, eventRoles, eventStartTime, eventName, eventSheetId, eventSlackChannel, eventbriteLink, emailTemplate)
                send(services.googleAPI, vertx, config,subject, emailContents, volunteer.email)
                    .onSuccess{
                        log.info "Recruitment email sent to ${volunteer.email}"
                        updateVolunteerStatus(services.googleAPI, eventSheetId, volunteer.email, resultingStatus, null)
                        blocking.complete()
                    }
            }).onFailure{
                err-> log.error "Encountered an error sending email to ${volunteer.email}"
                        log.error err.getMessage(), err
            }

            if(lastFuture != null){
                lastFuture.compose(done->future)
            }

            lastFuture = future

        }
    }

    lastFuture.onSuccess{
        log.info "Sent all recruitment emails"
        services.db.markTaskComplete(task.taskId)

        if(task.notify){
            sendSlackMessage(services.slackAPI, eventSlackChannel, "Sent recruitment emails for ${eventName}!")
        }
    }.onFailure{err->
        log.error "Error while sending emails!"
        log.error err.getMessage(),err
    }



}

/**
 * NOTE: Be sure this executes inside vertx.blocking...
 * @param googleAPI
 * @param vertx
 * @param config
 * @param subject
 * @param emailBody
 * @param target
 * @return
 */
private static Future send(GoogleAPI googleAPI, Vertx vertx, config, subject, emailBody, target){
    Promise promise = Promise.promise()

    Thread.sleep(config.getLong("mass_email_delay"))
    sendEmail(googleAPI, "AutoWiSE", target, subject, emailBody)
    promise.complete()

    return promise.future();
}

private static String makeShiftRoleEmail(services, Volunteer volunteer, Task task, Set<String> unfilledShiftRoles, List<Role> eventRoles, eventStartTime, eventName, eventSheetId, eventSlackChannel, eventbriteLink, emailTemplate){
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

    )
    services.db.insertWebhook(rejectHook)
    services.server.mountWebhook(rejectHook)

    def shiftRoleHTMLTable = buildShiftRoleOptions(availableShiftRoles)
    def emailContents = emailTemplate.replaceAll("%AVAILABLE_SHIFT_ROLES%", shiftRoleHTMLTable)
    emailContents = emailContents.replaceAll("%REJECT_LINK%", "<a href=\"http://localhost:8080/${rejectHook.path()}\">Click me if you aren't able to volunteer for this event.</a>")
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