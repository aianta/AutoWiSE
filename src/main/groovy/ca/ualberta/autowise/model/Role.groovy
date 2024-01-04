package ca.ualberta.autowise.model

import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray

class Role {
    String name
    String description
    List<Shift> shifts = new ArrayList()



}
