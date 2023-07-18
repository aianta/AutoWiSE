package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field

import java.time.ZonedDateTime
import java.util.stream.Collectors

import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.slurpVolunteerList
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.appendAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage


/**
 * @author Alexandru Ianta
 *
 * The following task is broken down as follows:
 *
 * 1. Fetch the volunteer pool and fetch the email template
 *      a. Fetch the 'Volunteer Contact Status' table
 *      b. Compare the volunteer pool to the list on the 'Volunteer Contact Status' sheet. Append volunteers that don't appear in the sheet.
 * 2. Assemble the Shift-Role volunteer options summary for the event.
 * 3. One-by-one for each Volunteer marked 'Not Contacted': generate unique webhooks for each volunteer and send them the recruitment email.
 *    a. After the email is sent, update the 'Volunteer Contact Status' sheet with status 'Waiting for response' and the 'Last Contacted' value
 *    b. Pick one random volunteer whose email will be BCC'd to volunteer coordinators for quality control.
 * 4. Mark our task complete in SQLite
 * 5. If the task has the notify flag turned on, notify slack that the task has been completed.
 *
 */

@Field static def VOLUNTEER_CONTACT_STATUS_RANGE = "\'Volunteer Contact Status\'!A:G"

static def initialRecruitmentEmailTask(services, Task task, volunteerPoolSheetId, volunteerPoolTableRange){

    def volunteers = slurpVolunteerList(services.googleAPI, volunteerPoolSheetId, volunteerPoolTableRange)
    def eventSheetId = task.data.getString("eventSheetId")
    def volunteerContactStatusData = getValuesAt(services.googleAPI, eventSheetId, VOLUNTEER_CONTACT_STATUS_RANGE)
    volunteerContactStatusData = updateVolunteerList(services.googleAPI, eventSheetId, volunteers, volunteerContactStatusData)

    def it = volunteerContactStatusData.listIterator()
    while (it.hasNext()){
        def rowData = (List<String>)it.next()
        if (rowData.get(0).equals("Volunteers")){
            continue // Skip header row
        }

        //Assemble email for this volunteer.
        def volunteerName = getVolunteerByEmail(rowData.get(0)).name

        //Send email

        //Update volunteer contact status data
        rowData.set(1, ZonedDateTime.now().format(EventSlurper.eventTimeFormatter)) //Update 'Last Contacted at'
        rowData.set(2, 'Waiting for response')

        //Update the volunteer contact status
        updateVolunteerContactStatusTable(services.googleAPI, eventSheetId, volunteerContactStatusData)
    }

    //Mark task complete in SQLite
    services.db.markTaskComplete(task.taskId)

    //Check notify flag, and notify if required
    if(task.notify){
        sendSlackMessage(services.slackAPI, task.data.getString("eventSlackChannel"), "Initial recruitment emails have been sent successfully!")
    }
}

private static def updateVolunteerContactStatusTable(GoogleAPI googleAPI, sheetId, data){
    def valueRange = new ValueRange()
    valueRange.setRange(VOLUNTEER_CONTACT_STATUS_RANGE)
    valueRange.setMajorDimension("ROWS")
    valueRange.setValues(data)
    updateAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE, valueRange )
}

private static def getVolunteerByEmail(String email, Set<Volunteer> volunteers){
    return volunteers.stream().filter (volunteer->volunteer.email.equals(email))
        .findFirst().orElse(null);
}

/**
 * Adds any volunteers that have been added to the volunteer pool into the volunteer
 * contact status sheet for this event.
 * @param googleAPI
 * @param sheetId
 * @param mainPool
 * @param volunteerContactStatusTable
 * @return
 */
private static def updateVolunteerList(GoogleAPI googleAPI, sheetId, mainPool, volunteerContactStatusTable) {

    def stringSetVolunteers = mainPool.stream().map(volunteer -> volunteer.email).collect(Collectors.toSet());
    def volunteerContactStatusSet = [] as Set
    def toAdd = [] as Set

    //Go through the current list and build it into a set
    def it = volunteerContactStatusTable.listIterator()
    while (it.hasNext()) {
        def rowData = it.next()
        if (rowData.get(0).equals("Volunteers")) {
            continue //Skip header row
        }
        volunteerContactStatusSet.add(rowData.get(0))
    }

    //Find all volunteers that appear in the main volunteer pool but not in the contact status page
    for (String volunteer : stringSetVolunteers) {
        if (!volunteerContactStatusSet.contains(volunteer)) {
            toAdd.add(volunteer)
        }
    }

    //Create records for all of them.
    def valueRange = new ValueRange()
    valueRange.setRange(VOLUNTEER_CONTACT_STATUS_RANGE)
    valueRange.setValues(makeNewVolunteerEntries(toAdd))
    valueRange.setMajorDimension("ROWS")

    //Append them into the volunteer contact status range
    appendAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE, valueRange )

    //Get the updated volunteer contact status range
    return getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE)
}

private static makeNewVolunteerEntries(volunteers){
    def result = []
    volunteers.forEach{
        volunteer->
            result.add([volunteer, "-", "Not Contacted", "-", "-", "-","-"])
    }
}
