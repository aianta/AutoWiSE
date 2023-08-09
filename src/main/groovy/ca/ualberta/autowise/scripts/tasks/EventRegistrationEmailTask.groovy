package ca.ualberta.autowise.scripts.tasks

import ca.ualberta.autowise.AutoWiSE
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory

import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.stream.Collectors

import static ca.ualberta.autowise.JsonUtils.slurpRolesJson
import static ca.ualberta.autowise.scripts.BuildShiftRoleOptions.buildShiftRoleOptions
import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.findAvailableShiftRoles
import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.getShiftRole
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.SendEmail.sendEmailToGroup
import static ca.ualberta.autowise.scripts.webhook.SignupForRoleShift.makeAssignedEmail
import static ca.ualberta.autowise.scripts.webhook.SignupForRoleShift.makeWaitlistEmail
import static ca.ualberta.autowise.scripts.tasks.ConfirmationEmailTask.makeConfirmEmail
import static ca.ualberta.autowise.scripts.webhook.RejectVolunteeringForEvent.makeRejectedEmail

/**
 * @author Alexandru Ianta
 * Sends an email to event leads and volunteer coordinators to let them know
 * their event has been successfully registered into the system.
 *
 */

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.tasks.EventRegistrationEmailTask.class)

//TODO - preview all emails + start campaign link
static Future eventRegistrationEmailTask(services, Task t, emailTemplateId, config){
    def recipients = new ArrayList<String>()
    def volunteerCoordinators = t.data.getJsonArray("volunteerCoordinators")
    def eventLeads = t.data.getJsonArray("eventLeads")
    def eventSheetId = t.data.getString("eventSheetId")
    def eventbriteLink = t.data.getString("eventbriteLink")
    def eventStartTime = ZonedDateTime.parse(t.data.getString("eventStartTime"), EventSlurper.eventTimeFormatter)
    def eventName = t.data.getString("eventName")
    List<Role> eventRoles =  slurpRolesJson(t.data.getString("rolesJsonString"))

    //Get all email templates that will be used in this campaign
    def initialRecruitmentEmailTemplateId = t.data.getString("initialRecruitmentEmailTemplateId")
    def recruitmentEmailTemplateId = t.data.getString("recruitmentEmailTemplateId")
    def confirmAssignedEmailTemplateId = t.data.getString("confirmAssignedEmailTemplateId")
    def confirmWaitlistEmailTemplateId = t.data.getString("confirmWaitlistEmailTemplateId")
    def confirmCancelledEmailTemplateId = t.data.getString("confirmCancelledEmailTemplateId")
    def confirmRejectedEmailTemplatedId = t.data.getString("confirmRejectedEmailTemplatedId")
    def followupEmailTemplateId = t.data.getString("followupEmailTemplateId")

    volunteerCoordinators.forEach {entry->recipients.add(entry)}
    eventLeads.forEach {entry->recipients.add(entry)}

    def campaignCancelHookPath = "/" + Base64.encodeBase64URLSafeString(t.data.getString("campaignCancelHookId").getBytes())
    def campaignBeginHookPath = "/" + Base64.encodeBase64URLSafeString(t.data.getString("campaignBeginHookId").getBytes())

    //Assemble the task summary for this campaign
    return services.db.fetchAllPendingTasksForEvent(t.eventId)
        .compose{eventTasks -> {

            def taskSummary = makeTaskSummary(eventTasks, t, config)

            CompositeFuture.all(
                    [slurpDocument(services.googleAPI, emailTemplateId),
                    slurpDocument(services.googleAPI, initialRecruitmentEmailTemplateId),
                    slurpDocument(services.googleAPI, recruitmentEmailTemplateId),
                    slurpDocument(services.googleAPI, confirmAssignedEmailTemplateId),
                    slurpDocument(services.googleAPI, confirmWaitlistEmailTemplateId),
                    slurpDocument(services.googleAPI, confirmCancelledEmailTemplateId),
                    slurpDocument(services.googleAPI, confirmRejectedEmailTemplatedId),
                    slurpDocument(services.googleAPI, followupEmailTemplateId),
                    findAvailableShiftRoles(services.googleAPI, eventSheetId)]
            ).compose{
                compositeResult->
                    def emailTemplate = compositeResult.resultAt(0)
                    def initialRecruitmentTemplate = compositeResult.resultAt(1)
                    def recruitmentTemplate = compositeResult.resultAt(2)
                    def confirmAssignedTemplate = compositeResult.resultAt(3)
                    def confirmWaitlistTemplate = compositeResult.resultAt(4)
                    def confirmCancelledTemplate = compositeResult.resultAt(5)
                    def confirmRejectedTemplate = compositeResult.resultAt(6)
                    def followupTemplate = compositeResult.resultAt(7)
                    def unfilledShiftRoles = compositeResult.resultAt(8)

                    def emailContents = emailTemplate.replaceAll("%eventName%", eventName)
                    emailContents = emailContents.replaceAll("%taskSummary%", taskSummary)
                    emailContents = emailContents.replaceAll("%cancelLink%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}${campaignCancelHookPath}\">Cancel Campaign</a>" )
                    emailContents = emailContents.replaceAll("%beginLink%","<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}${campaignBeginHookPath}\">Begin Campaign</a>" )
                    emailContents = emailContents.replaceAll("%templates%", makeTemplatesPreview(
                            initialRecruitmentTemplate,
                            recruitmentTemplate,
                            confirmAssignedTemplate,
                            confirmWaitlistTemplate,
                            confirmCancelledTemplate,
                            confirmRejectedTemplate,
                            followupTemplate,
                            ((Set<String>)unfilledShiftRoles),
                            eventRoles,
                            eventName,
                            eventbriteLink,
                            eventStartTime,
                            config
                    ))

                    //TODO - Error handling
                    return sendEmailToGroup(config, services.googleAPI, config.getString("sender_email"), recipients, "AutoWiSE Automated Campaign Plan for ${eventName}", emailContents)

            }.compose{
                services.db.markTaskComplete(t.taskId)
            }

        }}
}

private static def makeTemplatesPreview(
        initialRecruitmentTemplate,
        recruitmentTemplate,
        confirmAssignedTemplate,
        confirmWaitlistTemplate,
        confirmCancelledTemplate,
        confirmRejectedTemplate,
        followupTemplate,
        unfilledShiftRoles,
        eventRoles,
        String eventName,
        eventbriteLink,
        eventStartTime,
        config
){
    StringBuilder sb = new StringBuilder();
    ShiftRole sampleShiftRole = new ShiftRole(
            role: new Role(name: "Sample Role",
                    description: "A role which is used as an example for what a role might be."),
            shift: new Shift(startTime: LocalTime.now(AutoWiSE.timezone), endTime: LocalTime.now(AutoWiSE.timezone).plusHours(1), targetNumberOfVolunteers: 1, index: 1),
            shiftRoleString: '1-Sample Role',
            acceptHook: new Webhook(id:UUID.randomUUID())
    )
    def initialRecruitmentEmail = makeRecruitmentEmail(initialRecruitmentTemplate, unfilledShiftRoles, eventRoles, eventbriteLink, config)
    def recruitmentEmail = makeRecruitmentEmail(recruitmentTemplate, unfilledShiftRoles, eventRoles, eventbriteLink, config)
    def assignedEmail = makeAssignedEmail(confirmAssignedTemplate, sampleShiftRole , eventName, eventStartTime, new Webhook(id:UUID.randomUUID()), config )
    def waitlistEmail = makeWaitlistEmail(confirmWaitlistTemplate, sampleShiftRole, eventName)
    def cancelledEmail = confirmCancelledTemplate
    def rejectEmail = makeRejectedEmail(confirmRejectedTemplate, eventName)
    def followupEmail = makeConfirmEmail(followupTemplate, sampleShiftRole, "Testy McTest", eventbriteLink, new Webhook(id:UUID.randomUUID()), new Webhook(id:UUID.randomUUID()), config)

    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("Initial Recruitment Email")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append(initialRecruitmentEmail)
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("Recruitment Email")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append(recruitmentEmail)
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("Shift Role Assigned Email")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append(assignedEmail)
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("Waitlisted Email")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append(waitlistEmail)
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("Confirm Cancellation Email")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append(cancelledEmail)
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("Follow-up Email with signed up volunteers")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append(followupEmail)
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append("Rejected Volunteer opportunities for this event")
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")
    sb.append("<br><br>")
    sb.append(rejectEmail)
    sb.append("<br><br>")
    sb.append("-------------------------------------------------------")

    sb.append("<br><br>")



    return sb.toString()
}

/**
 * Need a special method here to mock the recruitment emails, this should always mirror the real ones though.
 * TODO: One day someone should refactor this to actually use the same code.
 * @param template
 * @param unfilledShiftRoles
 * @param eventRoles
 * @param eventbriteLink
 * @param config
 * @return
 */
private static def makeRecruitmentEmail(template, Set<String> unfilledShiftRoles, List<Role> eventRoles, eventbriteLink, config){
    log.info "Making mock recruitment email"
    def result = template
    List<ShiftRole> availableShiftRoles = unfilledShiftRoles.stream()
        .map(shiftRoleString->getShiftRole(shiftRoleString,eventRoles))
        .map(shiftRole->{
            //Mock hook
            Webhook volunteerWebhook = new Webhook(
                    id: UUID.randomUUID())
            shiftRole.acceptHook = volunteerWebhook
            return shiftRole
        })
        .collect(Collectors.toList())

    def shiftRoleHTMLTable = buildShiftRoleOptions(availableShiftRoles, config)

    Webhook rejectHook = new Webhook(
            id: UUID.randomUUID())

    result = result.replaceAll("%AVAILABLE_SHIFT_ROLES%", shiftRoleHTMLTable)
    result = result.replaceAll("%REJECT_LINK%", "<a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}/${rejectHook.path()}\">Click me if you aren't able to volunteer for this event.</a>")
    result = result.replaceAll("%EVENTBRITE_LINK%", "<a href=\"${eventbriteLink}\">eventbrite</a>")

    return result
}



private static def makeTaskSummary(List<Task> eventTasks, thisTask, config){
    StringBuilder sb = new StringBuilder()
    sb.append("<table><thead><tr><th>Task Name</th><th>Execution time</th><th>Cancel</th><th>Execute Now</th></tr></thead>")

    ListIterator<Task> it = eventTasks.listIterator();
    while (it.hasNext()){
        def currTask = it.next();
        if (currTask.taskId.equals(thisTask.taskId)){
            continue //Don't include this task in the task summary
        }

        def cancelHookPath = "/" + Base64.encodeBase64URLSafeString(currTask.data.getString("cancelHookId").getBytes())
        def executeHookPath = "/" + Base64.encodeBase64URLSafeString(currTask.data.getString("executeHookId").getBytes())

        sb.append("<tr><td>${currTask.name}</td>" +
                "<td>${currTask.taskExecutionTime.format(EventSlurper.eventTimeFormatter)}</td>" +
                "<td><a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}${cancelHookPath}\">Cancel</a></td>" +
                "<td><a href=\"${config.getString("protocol")}://${config.getString("host")}:${config.getInteger("port").toString()}${executeHookPath}\">Execute</a></td>" +
                "</tr>")
    }

    sb.append("</table>")

    return sb.toString()

}



