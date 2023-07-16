package ca.ualberta.autowise.model

import java.time.ZonedDateTime

enum EventStatus{
    READY, IN_PROGRESS, COMPLETE, PAUSE
}

class Event {


    UUID id
    String status

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
    double executiveRatio
    long campaignStartOffset //in milliseconds
    long resolicitFrequency //In milliseconds
    long followupOffset //Offset in milliseconds from start time when the follow up email should be sent.
    String recruitmentEmailTemplateId
    String followupEmailTemplateId
    String confirmAssignedEmailTemplateId
    String confrimWaitlistEmailTemplateId
    String confirmCancelledEmailTemplateId
    String confirmRejectedEmailTemplateId

    List<Role> roles

}
