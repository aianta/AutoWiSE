package ca.ualberta.autowise.model

import io.vertx.core.json.JsonObject

import java.beans.Transient
import java.time.LocalTime
import java.time.ZonedDateTime

class Shift {
    int index
    LocalTime startTime
    LocalTime endTime
    int targetNumberOfVolunteers


    def toJsonFormat(){
        JsonObject result = new JsonObject()
            .put("index", index)
            .put("startTime", startTime.toString())
            .put("endTime", endTime.toString())
            .put("targetNumberOfVolunteers", targetNumberOfVolunteers)

        return result
    }

    String toStringFormat(){
        return toJson().encodePrettily()
    }
}
