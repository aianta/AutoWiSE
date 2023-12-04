package ca.ualberta.autowise.model

import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray

class Role {
    UUID id;
    UUID eventId;
    String name
    String description
    List<Shift> shifts = new ArrayList()

    def removeShift(shiftIndex){
        shifts.removeIf {shift->shift.index == shiftIndex}
    }

//    def toJsonFormat(){
//        JsonObject result = new JsonObject()
//            .put("name", name)
//            .put("description", description)
//            .put("shifts", shifts.stream().map(Shift::toJsonFormat).collect(JsonArray::new, JsonArray::add, JsonArray::addAll))
//
//        return result
//    }
//
//    String toStringFormat(){
//        return toJson().encodePrettily()
//    }
}
