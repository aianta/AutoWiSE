package ca.ualberta.autowise.model

import io.vertx.core.json.JsonObject

import java.time.ZonedDateTime

enum TaskStatus{
    PENDING, SCHEDULED, CANCELLED, COMPLETE, IN_PROGRESS
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
    JsonObject data = new JsonObject()

    def makeCancelWebhook(){
        Webhook result = new Webhook(
                id: UUID.randomUUID(),
                eventId: eventId,
                type: HookType.CANCEL_TASK,
                expiry: taskExecutionTime.toInstant().toEpochMilli(),
                invoked: false,
                data: new JsonObject()
                    .put("taskId", taskId.toString())
        )
        return result
    }

    def makeExecuteWebhook(){
        Webhook result = new Webhook(
                id: UUID.randomUUID(),
                eventId: eventId,
                type: HookType.EXECUTE_TASK_NOW,
                expiry: taskExecutionTime.toInstant().toEpochMilli(),
                invoked: false,
                data: new JsonObject()
                    .put("taskId", taskId.toString())
        )
        return  result
    }

}

