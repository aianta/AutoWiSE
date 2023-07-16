package ca.ualberta.autowise.model

import java.time.ZonedDateTime

enum TaskStatus{
    SCHEDULED, CANCELLED, COMPLETE
}


/**
 * @author Alexandru Ianta
 * Describes a task to be done
 */
class Task {
    UUID taskId
    UUID eventId // The id of the event this task relates to.
    String name
    boolean advanceNotify //Whether the task should notify the event slack channel before it is executed.
    long advanceNotifyOffset //Milliseconds before task time to advance notify.
    boolean notify //Whether the task should notify the event slack channel after it is complete.
    ZonedDateTime taskExecutionTime //The datetime the task is scheduled to be executed.
    TaskStatus status
}
