package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.WaitlistEntry
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import org.slf4j.LoggerFactory

import java.time.ZonedDateTime
import java.util.stream.Collectors

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.appendAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt
import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValuesAt


@Field static def VOLUNTEER_CONTACT_STATUS_RANGE = "\'Volunteer Contact Status\'!A:H"
@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.class)


static def getWaitlistForShiftRole(GoogleAPI googleAPI, sheetId, shiftRoleString){
    def result = new ArrayList<WaitlistEntry>();

    def volunteerStatusData = getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE)

    def it = volunteerStatusData.iterator();

    while(it.hasNext()){
        def rowData = it.next()
        if(!rowData.isEmpty() && rowData.get(7).equals(shiftRoleString) && rowData.get(2).equals("Waitlisted") && !rowData.get(6).equals("-")){
            result.add(new  WaitlistEntry(
                    email: rowData.get(0),
                    waitlistedOn: ZonedDateTime.parse(rowData.get(6), EventSlurper.eventTimeFormatter),
                    desiredShiftRole: rowData.get(7)
            ))
        }
    }

    //Sort the waitlisted entries in ascending order such that the oldest entry is first,
    result = result.sort((entry1, entry2)->{
        return entry1.waitlistedOn.compareTo(entry2.waitlistedOn)
    })

    return result
}

static def hasVolunteerAlreadyCancelled(GoogleAPI googleAPI, sheetId, volunteerEmail){
    def volunteerStatusData = getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE)

    //Get the row for the specified volunteer
    def volunteerRow = volunteerStatusData.stream().filter(rowData->!rowData.isEmpty() && rowData.get(0).equals(volunteerEmail)).findFirst().orElse(null);

    if (volunteerRow == null){
        log.warn "Warning, could not find volunteer with email ${volunteerEmail} to check if they've already cancelled."
        return true //Gong to return true anyways, to halt any operation being done.
    }

    return !volunteerRow.get(5).equals("-") || !volunteerRow.get(4).equals("-") //Index 5 corresponds with 'Cancelled On' in the sheet. Index 4 corresponds with 'Rejected On' If the value is something other than '-' they have already cancelled/rejected.

}

static def updateVolunteerStatus(GoogleAPI googleAPI, sheetId, volunteerEmail, volunteerStatus, shiftRoleString){

    def volunteerStatusData = getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE)

    def it = volunteerStatusData.iterator();
    while (it.hasNext()){
        def rowData = it.next();
        if(!rowData.isEmpty() && rowData.get(0).equals(volunteerEmail)){
            rowData.set(2, volunteerStatus)
            if (shiftRoleString != null){
                rowData.set(7, shiftRoleString)
            }
            switch (volunteerStatus){
                case "Accepted":
                    rowData.set(3, ZonedDateTime.now().format(EventSlurper.eventTimeFormatter))
                    break
                case "Rejected":
                    rowData.set(4, ZonedDateTime.now().format(EventSlurper.eventTimeFormatter))
                    break
                case "Cancelled":
                    rowData.set(5, ZonedDateTime.now().format(EventSlurper.eventTimeFormatter))
                    break
                case "Waitlisted":
                    rowData.set(6, ZonedDateTime.now().format(EventSlurper.eventTimeFormatter))
                    break
                case "Waiting for response":
                    rowData.set(1, ZonedDateTime.now().format(EventSlurper.eventTimeFormatter))
                    break
                default:
                    log.warn "Unrecognized volunteer status string!"
            }

            break
        }
    }
    updateVolunteerContactStatusTable(googleAPI, sheetId, volunteerStatusData)
}

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
