package ca.ualberta.autowise.model

import java.time.ZonedDateTime

enum EventStatus{
    PENDING,READY, IN_PROGRESS, COMPLETE, CANCELLED
}

class Event {


    UUID id
    String status
    String sheetId

    //Event info
    String name
    String description
    List<String> eventOrganizers
    List<String> volunteerCoordinators
    ZonedDateTime startTime
    ZonedDateTime endTime
    String eventbriteLink
    String eventSlackChannel

    //Recruitment campaign info
    ZonedDateTime campaignStart
    ZonedDateTime followupTime;
    long resolicitFrequency //In milliseconds
    //long followupOffset //Offset in milliseconds from start time when the follow up email should be sent.
    String initialRecruitmentEmailTemplateId
    String recruitmentEmailTemplateId
    String followupEmailTemplateId
    String confirmAssignedEmailTemplateId
    String confirmWaitlistEmailTemplateId
    String confirmCancelledEmailTemplateId
    String confirmRejectedEmailTemplateId

    List<Role> roles

}
