package ca.ualberta.autowise.model

import java.time.ZonedDateTime

class ContactStatus {



    UUID eventId;
    String sheetId;
    String volunteerEmail;
    String status;
    ZonedDateTime lastContacted;
    String desiredShiftRole;
    ZonedDateTime acceptedOn;
    ZonedDateTime rejectedOn;
    ZonedDateTime cancelledOn;
    ZonedDateTime waitlistedOn;





}
