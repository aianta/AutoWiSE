package ca.ualberta.autowise.scripts.tasks

import ca.ualberta.autowise.AutoWiSE
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.scripts.google.EventSlurper
import org.apache.commons.codec.binary.Base64

import static ca.ualberta.autowise.scripts.google.FindEvent.findEvent
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.SendEmail.sendEmailToGroup

/**
 * @author Alexandru Ianta
 * Sends an email to event leads and volunteer coordinators to let them know
 * their event has been successfully registered into the system.
 *
 */


static def eventRegistrationEmailTask(services, Task t, emailTemplateId, config){
    def recipients = new ArrayList<String>()
    def volunteerCoordinators = t.data.getJsonArray("volunteerCoordinators")
    def eventLeads = t.data.getJsonArray("eventLeads")
    volunteerCoordinators.forEach {entry->recipients.add(entry)}
    eventLeads.forEach {entry->recipients.add(entry)}

    def eventName = t.data.getString("eventName")
    def campaignCancelHookPath = "/" + Base64.encodeBase64URLSafeString(t.data.getString("campaignCancelHookId").getBytes())

    //Assemble the task summary for this campaign
    services.db.fetchAllScheduledTasksForEvent(t.eventId)
        .onSuccess(eventTasks->{

            def taskSummary = makeTaskSummary(eventTasks, t, config)
            //NOTE: Do as I say, not as I do...
            def emailTemplate = slurpDocument(services.googleAPI, emailTemplateId)

            def emailContents = emailTemplate.replaceAll("%eventName%", eventName)
            emailContents = emailContents.replaceAll("%taskSummary%", taskSummary)
            emailContents = emailContents.replaceAll("%cancelLink%", "<a href=\"http://${config.getString("host")}:${config.getInteger("port").toString()}${campaignCancelHookPath}\">Cancel Campaign</a>" )

            //TODO - Error handling
            sendEmailToGroup(services.googleAPI, "AutoWiSE", recipients, "AutoWiSE Automated Campaign Plan for ${eventName}", emailContents)
            services.db.markTaskComplete(t.taskId)
        })



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
                "<td><a href=\"http://${config.getString("host")}:${config.getInteger("port").toString()}${cancelHookPath}\">Cancel</a></td>" +
                "<td><a href=\"http://${config.getString("host")}:${config.getInteger("port").toString()}${executeHookPath}\">Execute</a></td>" +
                "</tr>")
    }

    sb.append("</table>")

    return sb.toString()

}



