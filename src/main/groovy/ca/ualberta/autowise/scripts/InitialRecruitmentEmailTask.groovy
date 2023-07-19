package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.core.json.JsonObject

import java.time.ZonedDateTime
import java.util.stream.Collectors

import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.getShiftRole
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.slurpVolunteerList
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.appendAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt
import static ca.ualberta.autowise.JsonUtils.slurpRolesJson
import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.findAvailableShiftRoles
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage
import static ca.ualberta.autowise.scripts.SyncEventVolunteerContactSheet.syncEventVolunteerContactSheet
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.BuildShiftRoleOptions.buildShiftRoleOptions
import static ca.ualberta.autowise.scripts.google.SendEmail.*

/**
 * @author Alexandru Ianta
 *
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


static def initialRecruitmentEmailTask(services, Task task, volunteerPoolSheetId, volunteerPoolTableRange){

    // Fetch all the data we'll need to execute the task
    def volunteers = slurpVolunteerList(services.googleAPI, volunteerPoolSheetId, volunteerPoolTableRange)
    def eventName = task.data.getString("eventName")
    def eventSheetId = task.data.getString("eventSheetId")
    def eventbriteLink = task.geta.getString("eventbriteLink")
    List<Role> eventRoles = slurpRolesJson(task.data.getString("rolesJsonString"))
    def eventStartTime = ZonedDateTime.parse(task.data.getString("eventStartTime"), EventSlurper.eventTimeFormatter)
    def volunteerContactStatusData = syncEventVolunteerContactSheet(services.googleAPI, eventSheetId, volunteers)
    def emailTemplate = slurpDocument(services.googleAPI, task.data.getString("emailTemplateId"))
    def unfilledShiftRoles = findAvailableShiftRoles(services.googleAPI, eventSheetId)

    if (unfilledShiftRoles.size() == 0){
        return handleNoAvailableShiftRoles(services, task, eventName)
    }

    def it = volunteerContactStatusData.listIterator()
    while (it.hasNext()){
        def rowData = it.next()
        if (rowData.get(0).equals("Volunteers")){
            continue // Skip header row
        }

        //Assemble email for this volunteer.
        def volunteer = getVolunteerByEmail(rowData.get(0))

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
                                    .put("eventSheetId", eventSheetId)
                                    .put("shiftRoleString", shiftRole.shiftRoleString)
                    )
                    services.db.insertWebhook(volunteerWebhook)
                    services.server.mountWebhook(volunteerWebhook)
                    shiftRole.acceptHook = volunteerWebhook
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

        //Send email
        sendEmail(services.googleAPI, "AutoWise", volunteer.email, "[WiSER] Volunteer opportunities for ${eventName}!",emailContents)

        //Update volunteer contact status data
        rowData.set(1, ZonedDateTime.now().format(EventSlurper.eventTimeFormatter)) //Update 'Last Contacted at'
        rowData.set(2, 'Waiting for response')

        //Update the volunteer contact status
        updateVolunteerContactStatusTable(services.googleAPI, eventSheetId, volunteerContactStatusData)
    }

    //Mark task complete in SQLite
    services.db.markTaskComplete(task.taskId)

    //Check notify flag, and notify if required
    if(task.notify){
        sendSlackMessage(services.slackAPI, task.data.getString("eventSlackChannel"), "Initial recruitment emails have been sent successfully!")
    }
}

private static def handleNoAvailableShiftRoles(services, Task task, eventName){
    services.db.markTaskComplete(task.taskId)
    if(task.notify){
        sendSlackMessage(services.slackAPI, task.data.getString("eventSlackChannel"), "No unfilled shifts for ${eventName}. Aborting recruitment email task.")
    }
    return
}

private static def getVolunteerByEmail(String email, Set<Volunteer> volunteers){
    return volunteers.stream().filter (volunteer->volunteer.email.equals(email))
        .findFirst().orElse(null);
}




