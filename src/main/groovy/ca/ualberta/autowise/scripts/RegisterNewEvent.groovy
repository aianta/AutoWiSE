package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.AutoWiSE
import ca.ualberta.autowise.model.ContactStatus
import ca.ualberta.autowise.utils.JsonUtils
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Shift
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.TaskStatus
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.drive.model.File
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors
import java.util.stream.Stream

import io.vertx.core.json.JsonArray

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateSingleValueAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateColumnValueAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.slurpVolunteerList
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.clearRange
import static ca.ualberta.autowise.scripts.google.GetFilesInFolder.*


@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.RegisterNewEvent.class)
@Field static def EVENT_STATUS_ROLE_SHIFT_CELL_ADDRESS = "\'Event Status\'!A5"

static def registerNewEvent(services, event, config){

    log.info "Starting new event registration!"
    /**
     * Registering a new event means:
     *
     *
     * 2. Creating an automated recruitment campaign plan
     *      This will consist of a series of scheduled tasks to execute for this event.
     * 3. Create a 'begin campaign' webhook
     * 4. Create 'cancel campaign' webhook
     * 4. Create webhooks to cancel and instantly execute any task.
     * 5. Update the Event Status in the database.
     * 6. Initialize the 'Volunteer Contact Status' entries in the database for this event
     * 7. Set the event status to 'IN_PROGRESS'
     */



    // Make campaign plan
    List<Task> plan = makeCampaignPlan(event)
    log.info "plan size: ${plan.size()}"

    Webhook beginHook = new Webhook(
            id: UUID.randomUUID(),
            eventId: event.id,
            type: HookType.BEGIN_CAMPAIGN,
            data: new JsonObject().put("eventSheetId", event.sheetId),
            expiry: ZonedDateTime.now(AutoWiSE.timezone).plusDays(3).toInstant().toEpochMilli(), //3 days to begin the campaign after it is registered
            invoked: false
    )
    services.db.insertWebhook(beginHook)
    services.server.mountWebhook(beginHook)
    plan.get(0).data.put("campaignBeginHookId", beginHook.id.toString())

    // Make, save, and mount cancel campaign webhook
    Webhook cancelHook = new Webhook(
            id: UUID.randomUUID(),
            eventId: event.id,
            type: HookType.CANCEL_CAMPAIGN,
            data: new JsonObject().put("eventSheetId", event.sheetId),
            expiry: event.startTime.toInstant().toEpochMilli(),
            invoked: false,
    )
    services.db.insertWebhook(cancelHook)
    services.server.mountWebhook(cancelHook)
    plan.get(0).data.put("campaignCancelHookId", cancelHook.id.toString())

    //Make, save, and mount task webhooks
    plan.forEach {task->
        try{

            def taskCancelHook = task.makeCancelWebhook()
            services.db.insertWebhook(taskCancelHook)
            services.server.mountWebhook(taskCancelHook)
            task.data.put("cancelHookId", taskCancelHook.id.toString())

            def taskExecuteHook = task.makeExecuteWebhook()
            services.db.insertWebhook(taskExecuteHook)
            services.server.mountWebhook(taskExecuteHook)
            task.data.put("executeHookId", taskExecuteHook.id.toString())

        }catch(Exception e){
            log.error e.getMessage(), e
        }
    }

        return CompositeFuture.all(
                services.db.populateShiftRoles(event.id, event.sheetId, produceRoleShiftList(event)),
                slurpVolunteerList(services.googleAPI, config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range")), //Get the volunteer list,
                services.db.updateEventStatus(event.id, EventStatus.PENDING)
        ).compose{
            composite->

                Set<Volunteer> volunteers = composite.resultAt(1)
                log.info "got volunteers! ${volunteers}"

                List<ContactStatus> contactStatuses = new ArrayList<>();
                volunteers.forEach {
                    contactStatuses.add(new ContactStatus(
                            eventId: event.id,
                            sheetId: event.sheetId,
                            volunteerEmail: it.email,
                            status: "Not Contacted"
                    ))
                }

                return CompositeFuture.all(contactStatuses.stream().map {services.db.insertContactStatus(it)}.collect(Collectors.toList()))
                        .onFailure {log.error it.cause.getMessage(), it.cause}
                        .onSuccess {
                    // Persist plan in sqlite, make sure to do this after webhook generation, so webhook info makes it into the db.
                    services.db.insertPlan(plan)
                }



        }
    }





private static def produceRoleShiftSet(Event event){
    return produceRoleShiftList(event).stream().collect(Collectors.toSet())
}

/**
 * For an event produces an expanded list of all volunteer slots as defined
 * by the number of roles and shifts.
 * IE: If there is a single role 'Grill Master' for which 2 volunteers are required
 * and this role has a single shift. This function would produce:
 *
 * ["1 - Grill Master", "1 - Grill Master"]
 * @param event
 */
private static def produceRoleShiftList(Event event){
    List<String> result = new ArrayList<>()

    event.roles.forEach(role->{
        role.shifts.forEach { Shift shift ->

            result.addAll(
                    Stream.generate(()->"${shift.index} - ${role.name}")
                            .limit(shift.targetNumberOfVolunteers)
                            .collect(Collectors.toList())
            )

        }
    })

    return result;

}




private static def makeCampaignPlan(Event event){
    List<Task> plan = new ArrayList<>();

    Task notifyEventLeadsAndVolCoordinatorsOfEventRegistration = new Task(
            taskId: UUID.randomUUID(),
            eventId: event.id,
            name: 'AutoWiSE Event Registration Email',
            taskExecutionTime: ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone),
            status: TaskStatus.SCHEDULED, //This is the only task that is actually scheduled when an event is registered.
            data: new JsonObject()
    )
    plan.add(notifyEventLeadsAndVolCoordinatorsOfEventRegistration)

    //If this event is being registered after when the initial recruitment email should have gone out, instead, send it now.
    def _initialRecruitmentTaskExecutionTime = event.campaignStart
    if(_initialRecruitmentTaskExecutionTime.isBefore(ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone))){
        _initialRecruitmentTaskExecutionTime = ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone)
    }

    Task initialRecruitmentEmail = new Task(
            taskId: UUID.randomUUID(),
            eventId: event.id,
            name: 'Initial Recruitment Email',
            taskExecutionTime: _initialRecruitmentTaskExecutionTime,
            status: TaskStatus.PENDING
    )
    initialRecruitmentEmail.data.put("emailTemplateId", event.initialRecruitmentEmailTemplateId)
    plan.add(initialRecruitmentEmail)

    /**
     * Compute appropriate number of reminder/resolicit emails
     * to send out based on event.resolicitFrequency
     */

    //Stop sending reminder emails 1 day before the event, even if the resolicit frequency says we should.
    def reminderEmailsCutoffTime = event.startTime.minus(1, ChronoUnit.DAYS)
    def firstEmailsTime = initialRecruitmentEmail.taskExecutionTime
    def resolicitTime = firstEmailsTime.plus(event.resolicitFrequency, ChronoUnit.MILLIS)

    while(resolicitTime.isBefore(reminderEmailsCutoffTime)){

        //It's possible an event is being registered late, and the start of the campaign has already passed.
        if (resolicitTime.isAfter(ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone))){
            Task reminder = new Task(
                    taskId: UUID.randomUUID(),
                    eventId: event.id,
                    name: 'Recruitment Email',
                    taskExecutionTime: resolicitTime,
                    status: TaskStatus.PENDING,
                    data: new JsonObject()
                        .put("emailTemplateId", event.recruitmentEmailTemplateId)
            )
            plan.add(reminder)
        }

        resolicitTime = resolicitTime.plus(event.resolicitFrequency, ChronoUnit.MILLIS)

    }

    //Add a follow-up email reminding volunteers who've been assigned shifts about their commitment.
    Task followup = new Task(
            taskId: UUID.randomUUID(),
            eventId: event.id,
            name: 'Follow-up Email',
            taskExecutionTime: event.followupTime,
            status: TaskStatus.PENDING,
            data: new JsonObject()
                    .put("eventSlackChannel", event.eventSlackChannel)
                    .put("emailTemplateId", event.followupEmailTemplateId)
                    .put("confirmAssignedEmailTemplateId", event.confirmAssignedEmailTemplateId)
                    .put("confirmCancelledEmailTemplateId", event.confirmCancelledEmailTemplateId)
    )
    plan.add(followup)

    return plan
}