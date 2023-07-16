package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.TaskStatus
import groovy.transform.Field
import io.vertx.core.Promise
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount

import static ca.ualberta.autowise.scripts.google.UpdateSheetSingleValue.updateSingleValueAt

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.RegisterNewEvent.class)

static def registerNewEvent(services, event, sheetId){
    Promise promise = Promise.promise();

    log.info "Starting new event registration!"
    /**
     * Registering a new event means:
     *
     * 1. Assigning it an event id
     * 2. Creating an automated recruitment campaign plan
     *      This will consist of a series of scheduled tasks to execute for this event.
     * 3. Generate 'Event Status' sheet in the event spreadsheet.
     * 4. Generate 'Volunteer Contact Status' sheet in the event spreadsheet.
     * 5. Set the event status to 'IN_PROGRESS' in the event spreadsheet.
     * 6. Notify volunteer coordinators and event leads that the event has been registered.
     */

    assert event.id == null // Events processed by this script should not have ids
    event.id = UUID.randomUUID() // Generate an id for this event.

    // Set event id
    //TODO uncomment this
    //updateSingleValueAt(services.googleAPI, sheetId, "Event!A2", event.id.toString())

    // Make campaign plan
    List<Task> plan = makeCampaignPlan(event)
    log.info "plan size: ${plan.size()}"
    // Persist plan in sqlite
    services.db.insertPlan(plan)

    return promise.future();
}

private static def makeCampaignPlan(Event event){
    List<Task> plan = new ArrayList<>();

    int sequenceNumber = 1

    Task notifyEventLeadsAndVolCoordinatorsOfEventRegistration = new Task(
            taskId: UUID.randomUUID(),
            eventId: event.id,
            name: 'AutoWiSE Event Registration Email',
            advanceNotify: false,
            advanceNotifyOffset: 0,
            notify: false,
            taskExecutionTime: ZonedDateTime.now(),
            status: TaskStatus.SCHEDULED
    )
    plan.add(notifyEventLeadsAndVolCoordinatorsOfEventRegistration)

    //If this event is being registered after when the initial recruitment email should have gone out, instead, send it now.
    def _initialRecruitmentTaskExecutionTime = event.startTime.minus(event.campaignStartOffset, ChronoUnit.MILLIS)
    def _initialRecruitmentTaskAdvanceNotifyOffset = Duration.ofDays(1).toMillis() // 1-day before
    def _initialRecruitmentTaskAdvancedNotify = true
    if(_initialRecruitmentTaskExecutionTime.isBefore(ZonedDateTime.now())){
        _initialRecruitmentTaskExecutionTime = ZonedDateTime.now()
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
            status: TaskStatus.SCHEDULED
    )
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
        if (resolicitTime.isAfter(ZonedDateTime.now())){
            Task reminder = new Task(
                    taskId: UUID.randomUUID(),
                    eventId: event.id,
                    name: 'Recruitment Email',
                    advanceNotify: true,
                    advanceNotifyOffset: Duration.ofDays(1).toMillis(),
                    notify: true,
                    taskExecutionTime: resolicitTime,
                    status: TaskStatus.SCHEDULED,
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
            status: TaskStatus.SCHEDULED,
    )
    plan.add(followup)

    return plan
}