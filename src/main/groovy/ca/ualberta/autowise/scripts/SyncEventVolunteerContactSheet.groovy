package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.Volunteer
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field

import java.util.stream.Collectors

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.appendAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt
import static ca.ualberta.autowise.scripts.google.VolunteerListSlurper.slurpVolunteerList
import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValuesAt


@Field static def VOLUNTEER_CONTACT_STATUS_RANGE = "\'Volunteer Contact Status\'!A:G"

static List<List<String>> syncEventVolunteerContactSheet(GoogleAPI googleAPI, sheetId , wiserVolunteers){
    //wiserVolunteers is a set of all wiser volunteers.
    //Fetch Volunteer Contact Status data for this event
    def volunteerContactStatusData = getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE)

    def stringSetVolunteers = wiserVolunteers.stream().map(volunteer -> volunteer.email).collect(Collectors.toSet());
    def volunteerContactStatusSet = [] as Set
    def toAdd = [] as Set

    //Go through the current list and build it into a set
    def it = volunteerContactStatusData.listIterator()
    while (it.hasNext()) {
        def rowData = it.next()
        if (rowData.get(0).equals("Volunteers")) {
            continue //Skip header row
        }
        volunteerContactStatusSet.add(rowData.get(0))
    }

    //Find all volunteers that appear in the set of all wiser volunteers but not in the contact status page
    for (String volunteer : stringSetVolunteers) {
        if (!volunteerContactStatusSet.contains(volunteer)) {
            toAdd.add(volunteer) //Add them to a set of volunteers to add to the event
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

static def updateVolunteerContactStatusTable(GoogleAPI googleAPI, sheetId, data){
    def valueRange = new ValueRange()
    valueRange.setRange(VOLUNTEER_CONTACT_STATUS_RANGE)
    valueRange.setMajorDimension("ROWS")
    valueRange.setValues(data)
    updateAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE, valueRange )
}

private static makeNewVolunteerEntries(volunteers){
    def result = []
    volunteers.forEach{
        volunteer->
            result.add([volunteer, "-", "Not Contacted", "-", "-", "-","-"])
    }
}