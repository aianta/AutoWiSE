package ca.ualberta.autowise.model

import ca.ualberta.autowise.scripts.google.EventSlurper
import io.vertx.sqlclient.Row

import java.time.ZonedDateTime

class VolunteerConfirmation {

    UUID eventId;
    String sheetId;
    String volunteerEmail;
    String volunteerName;
    String shiftRole;
    ZonedDateTime timestamp;


    public static VolunteerConfirmation fromRow(Row row){
        VolunteerConfirmation result = new VolunteerConfirmation();
        result.eventId = UUID.fromString(row.getString("event_id"))
        result.sheetId = row.getString("sheet_id")
        result.volunteerEmail = row.getString("volunteer_email")
        result.volunteerName = row.getString("volunteer_name")
        result.shiftRole = row.getString("shift_role")
        result.timestamp = row.getString("timestamp")?ZonedDateTime.parse(row.getString("timestamp"), EventSlurper.eventTimeFormatter):null


        return result;
    }
}
