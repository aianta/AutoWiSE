package ca.ualberta.autowise.utils

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.json.JsonGenerator
import groovy.json.JsonSlurper

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * @Author Alexandru Ianta
 *
 * Tools to facilitate JSON parsing and serialization.
 */

/**
 * Groovy's JSON support will freak-out with ZonedDateTimes. It also
 * produces undesired output for LocalTime. Generator below solves these
 * problems by outputting in the same format we read them from the sheet.
 * See: https://groovy-lang.org/processing-json.html#_customizing_output
 */
static def getEventGenerator(){
    return new JsonGenerator.Options()
            .addConverter(ZonedDateTime){date, key->
                return date.format(EventSlurper.eventTimeFormatter)
            }
            .addConverter(LocalTime){ time, key->
                return time.format(EventSlurper.shiftTimeFormatter)
            }
            .build()

}

static def slurpRolesJson(String json){
    def slurper = new JsonSlurper()
    def parsedJson = slurper.parseText(json)

    List<Role> result = new ArrayList<>()
    parsedJson.forEach { role->
        role.shifts.forEach { shift->

            shift.startTime = LocalTime.parse(shift.startTime, EventSlurper.shiftTimeFormatter)
            shift.endTime = LocalTime.parse(shift.endTime, EventSlurper.shiftTimeFormatter)
        }
        result.add(new Role(role))
    }
    return result
}

static def slurpEventJson(String json){
    def slurper = new JsonSlurper()
    def result = slurper.parseText(json) //This gives us a map

    result.id = UUID.fromString(result.id)
    result.startTime = ZonedDateTime.parse(result.startTime, EventSlurper.eventTimeFormatter)
    result.endTime = ZonedDateTime.parse(result.endTime, EventSlurper.eventTimeFormatter)

    result.campaignStart = ZonedDateTime.parse(result.campaignStart, EventSlurper.eventTimeFormatter);
    result.followupTime = ZonedDateTime.parse(result.followupTime, EventSlurper.eventTimeFormatter);

    result.roles.forEach { role->

        role.shifts.forEach { shift->

            shift.startTime = LocalTime.parse(shift.startTime, EventSlurper.shiftTimeFormatter)
            shift.endTime = LocalTime.parse(shift.endTime, EventSlurper.shiftTimeFormatter)

        }

    }

    return new Event(result)

}