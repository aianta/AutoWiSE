package ca.ualberta.autowise.model

import io.vertx.core.json.JsonObject
import org.apache.commons.codec.binary.Base64

import java.time.ZonedDateTime

enum HookType{
    ACCEPT_ROLE_SHIFT,
    REJECT_VOLUNTEERING_FOR_EVENT,
    CANCEL_ROLE_SHIFT,
    CANCEL_TASK,
    EXECUTE_TASK_NOW,
    CANCEL_CAMPAIGN
}

class Webhook {
    UUID id
    UUID eventId
    HookType type
    JsonObject data = new JsonObject() //Whatever data may be relevant to the action it triggers
    long expiry //Epoch milli after which the webhook is expired
    boolean invoked //Whether the webhook was invoked yet or not
    ZonedDateTime invokedOn //DateTime it was invoked on

    def path(){
        return Base64.encodeBase64URLSafeString(id.toString().getBytes())
    }

}
