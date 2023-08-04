package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.AutoWiSE
import ca.ualberta.autowise.JsonUtils
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Shift
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.TaskStatus
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors
import java.util.stream.Stream

import io.vertx.core.json.JsonArray

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateRowValueAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateSingleValueAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateColumnValueAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.slurpVolunteerList

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.RegisterNewEvent.class)
@Field static def EVENT_STATUS_ROLE_SHIFT_CELL_ADDRESS = "\'Event Status\'!A5"
@Field static def EVENT_STATUS_WAITLIST_CELL_ADDRESS = "\'Event Status\'!F4"

static def registerNewEvent(services, event, sheetId, volunteerSheetId, volunteerTableRange){

    log.info "Starting new event registration!"
    /**
     * Registering a new event means:
     *
     * 1. Assigning it an event id
     * 2. Creating an automated recruitment campaign plan
     *      This will consist of a series of scheduled tasks to execute for this event.
     * 3. Create a 'begin campaign' webhook
     * 4. Create 'cancel campaign' webhook
     * 4. Create webhooks to cancel and instantly execute any task.
     * 5. Update 'Event Status' sheet in the event spreadsheet.
     * 6. Initialize the 'Volunteer Contact Status' sheet in the event spreadsheet.
     * 7. Set the event status to 'IN_PROGRESS' in the event spreadsheet.
     */

    assert event.id == null // Events processed by this script should not have ids
    // Set the event id
    event.id = UUID.randomUUID() // Generate an id for this event.


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

    // Persist plan in sqlite, make sure to do this after webhook generation, so webhook info makes it into the db.
    services.db.insertPlan(plan)




    // TODO - batch these
    return CompositeFuture.all(
            updateColumnValueAt(services.googleAPI, sheetId, EVENT_STATUS_ROLE_SHIFT_CELL_ADDRESS, produceRoleShiftList(event)),        // Update 'Event Status' Sheet
            updateSingleValueAt(services.googleAPI, sheetId, "Event!A2", event.id.toString()),     //Update the event id in the sheet
            updateSingleValueAt(services.googleAPI, sheetId, "Event!A3", EventStatus.PENDING.toString()),     // Update the event status
            slurpVolunteerList(services.googleAPI, volunteerSheetId, volunteerTableRange) //Get the volunteer list
    ).compose{
        composite->

            def volunteers = composite.resultAt(3)
            log.info "got volunteers! ${volunteers}"
            def volunteerContactStatusData = makeInitialVolunteerContactStatus(volunteers)
            def volunteerContactStatusCellAddress = "\'Volunteer Contact Status\'!A2"
            ValueRange valueRange = new ValueRange()
            valueRange.setMajorDimension("ROWS")
            valueRange.setRange(volunteerContactStatusCellAddress)
            valueRange.setValues(volunteerContactStatusData)
            return updateAt(services.googleAPI, sheetId, volunteerContactStatusCellAddress, valueRange)


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

private static def makeInitialVolunteerContactStatus(Set<Volunteer> volunteers){

    def result = []
    volunteers.forEach {volunteer->
        result.add([volunteer.email, "-", "Not Contacted","-","-","-","-","-"])
    }

    return result
}


private static def makeCampaignPlan(Event event){
    List<Task> plan = new ArrayList<>();

    Task notifyEventLeadsAndVolCoordinatorsOfEventRegistration = new Task(
            taskId: UUID.randomUUID(),
            eventId: event.id,
            name: 'AutoWiSE Event Registration Email',
            advanceNotify: false,
            advanceNotifyOffset: 0,
            notify: false,
            taskExecutionTime: ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone),
            status: TaskStatus.SCHEDULED, //This is the only task that is actually scheduled when an event is registered.
            data: new JsonObject()
                .put("eventName", event.name)
                .put("eventSheetId", event.sheetId)
                .put("eventbriteLink", event.eventbriteLink)
                .put("eventStartTime", event.startTime.format(EventSlurper.eventTimeFormatter))
                .put("rolesJsonString", JsonUtils.getEventGenerator().toJson(event.roles))
                .put("eventLeads", event.eventOrganizers.stream().collect(JsonArray::new, JsonArray::add, JsonArray::addAll))
                .put("volunteerCoordinators", event.volunteerCoordinators.stream().collect(JsonArray::new, JsonArray::add, JsonArray::addAll))
                .put("initialRecruitmentEmailTemplateId", event.initialRecruitmentEmailTemplateId)
                .put("recruitmentEmailTemplateId", event.recruitmentEmailTemplateId)
                .put("confirmAssignedEmailTemplateId", event.confirmAssignedEmailTemplateId)
                .put("confirmWaitlistEmailTemplateId", event.confrimWaitlistEmailTemplateId)
                .put("confirmCancelledEmailTemplateId", event.confirmCancelledEmailTemplateId)
                .put("confirmRejectedEmailTemplatedId", event.confirmRejectedEmailTemplateId)
                .put("followupEmailTemplateId", event.followupEmailTemplateId)
    )
    plan.add(notifyEventLeadsAndVolCoordinatorsOfEventRegistration)

    //If this event is being registered after when the initial recruitment email should have gone out, instead, send it now.
    def _initialRecruitmentTaskExecutionTime = event.startTime.minus(event.campaignStartOffset, ChronoUnit.MILLIS)
    def _initialRecruitmentTaskAdvanceNotifyOffset = Duration.ofDays(1).toMillis() // 1-day before
    def _initialRecruitmentTaskAdvancedNotify = true
    if(_initialRecruitmentTaskExecutionTime.isBefore(ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone))){
        _initialRecruitmentTaskExecutionTime = ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone)
        _initialRecruitmentTaskAdvanceNotifyOffset = 0L //No more advanced notification
        _initialRecruitmentTaskAdvancedNotify = false

    }

    Task initialRecruitmentEmail = new Task(
            taskId: UUID.randomUUID(),
            eventId: event.id,
            name: 'Initial Recruitment Email',
            advanceNotify: _initialRecruitmentTaskAdvancedNotify,
            advanceNotifyOffset: _initialRecruitmentTaskAdvanceNotifyOffset,
            notify: true,
            taskExecutionTime: _initialRecruitmentTaskExecutionTime,
            status: TaskStatus.PENDING
    )
    initialRecruitmentEmail.data.put("eventSheetId", event.sheetId)
    initialRecruitmentEmail.data.put("eventSlackChannel", event.eventSlackChannel)
    initialRecruitmentEmail.data.put("rolesJsonString", JsonUtils.getEventGenerator().toJson(event.roles))
    initialRecruitmentEmail.data.put("eventStartTime", event.startTime.format(EventSlurper.eventTimeFormatter))
    initialRecruitmentEmail.data.put("emailTemplateId", event.initialRecruitmentEmailTemplateId)
    initialRecruitmentEmail.data.put("confirmAssignedEmailTemplateId", event.confirmAssignedEmailTemplateId)
    initialRecruitmentEmail.data.put("confirmWaitlistEmailTemplateId", event.confrimWaitlistEmailTemplateId)
    initialRecruitmentEmail.data.put("confirmCancelledEmailTemplateId", event.confirmCancelledEmailTemplateId)
    initialRecruitmentEmail.data.put("confirmRejectedEmailTemplatedId", event.confirmRejectedEmailTemplateId)
    initialRecruitmentEmail.data.put("eventbriteLink", event.eventbriteLink)
    initialRecruitmentEmail.data.put("eventName", event.name)
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
                    advanceNotify: true,
                    advanceNotifyOffset: Duration.ofDays(1).toMillis(),
                    notify: true,
                    taskExecutionTime: resolicitTime,
                    status: TaskStatus.PENDING,
                    data: new JsonObject()
                        .put("eventSheetId", event.sheetId)
                        .put("eventSlackChannel", event.eventSlackChannel)
                        .put("rolesJsonString", JsonUtils.getEventGenerator().toJson(event.roles))
                        .put("eventStartTime", event.startTime.format(EventSlurper.eventTimeFormatter))
                        .put("emailTemplateId", event.recruitmentEmailTemplateId)
                        .put("confirmAssignedEmailTemplateId", event.confirmAssignedEmailTemplateId)
                        .put("confirmWaitlistEmailTemplateId", event.confrimWaitlistEmailTemplateId)
                        .put("confirmCancelledEmailTemplateId", event.confirmCancelledEmailTemplateId)
                        .put("confirmRejectedEmailTemplateId", event.confirmRejectedEmailTemplateId)
                        .put("eventbriteLink", event.eventbriteLink)
                        .put("eventName", event.name)
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
            advanceNotify: true,
            advanceNotifyOffset: Duration.ofDays(1).toMillis(),
            notify: true,
            taskExecutionTime: event.startTime.minus(event.followupOffset, ChronoUnit.MILLIS),
            status: TaskStatus.PENDING,
            data: new JsonObject()
                    .put("eventSheetId", event.sheetId)
                    .put("eventSlackChannel", event.eventSlackChannel)
                    .put("emailTemplateId", event.followupEmailTemplateId)
                    .put("eventbriteLink", event.eventbriteLink)
                    .put("eventName", event.name)
                    .put("eventStartTime", event.startTime.format(EventSlurper.eventTimeFormatter))
                    .put("rolesJsonString", JsonUtils.getEventGenerator().toJson(event.roles))
                    .put("confirmAssignedEmailTemplateId", event.confirmAssignedEmailTemplateId)
                    .put("confirmCancelledEmailTemplateId", event.confirmCancelledEmailTemplateId)
    )
    plan.add(followup)

    return plan
}