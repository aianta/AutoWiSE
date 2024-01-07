package ca.ualberta.autowise.scripts.google

/**
 * @Author Alexandru Ianta
 * Collection of constants for working with event spreadsheets and event date values.
 */


import groovy.transform.Field

import org.slf4j.LoggerFactory

import java.time.format.DateTimeFormatter



@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.EventSlurper.class)
@Field static ROLES_AND_SHIFTS_RANGE = "Event!A29:D200"



/**
 * DateTime parsing for event start and end times from google sheets.
 * See: <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">DateTimeFormatter docs<a>
 */
@Field static DateTimeFormatter eventTimeFormatter = DateTimeFormatter.ofPattern("M/dd/yyyy HH:mm:ss z")

/**
 * LocalTime parsing for shift start and end times from google sheets.
 * See: <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">DateTimeFormatter docs<a>
 */
@Field static DateTimeFormatter shiftTimeFormatter = DateTimeFormatter.ofPattern("H:mm");


@Field static def staticSingleValues = [
        "id": "Event!A2",
        "status":"Event!A3",
        "name": "Event!B5",
        "description":"Event!B6",
        "eventStartTime": "Event!B9",
        "eventEndTime": "Event!B10",
        "eventbriteLink": "Event!B11",
        "eventSlackChannel": "Event!B12",
        "campaignStartOffset": "Event!B16",
        "resolicitFrequency": "Event!B17",
        "followupOffset": "Event!B18",
        "initialRecruitmentEmailTemplateId": "Event!B19",
        "recruitmentEmailTemplateId": "Event!B20",
        "followupEmailTemplateId": "Event!B21",
        "confirmAssignedEmailTemplateId": "Event!B22",
        "confirmCancelledEmailTemplateId": "Event!B23",
        "confirmWaitlistEmailTemplateId": "Event!B24",
        "confirmRejectedEmailTemplateId": "Event!B25",
]

