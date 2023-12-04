package ca.ualberta.autowise.model

import java.time.ZonedDateTime

class VolunteerConfirmation {

    UUID eventId;
    String sheetId;
    String volunteerEmail;
    String volunteerName;
    String shiftRole;
    ZonedDateTime timestamp;

}
