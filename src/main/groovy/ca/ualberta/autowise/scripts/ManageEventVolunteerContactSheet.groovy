package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.WaitlistEntry
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName
import io.vertx.core.Future
import io.vertx.core.Promise
import org.slf4j.LoggerFactory

import java.time.ZonedDateTime
import java.util.stream.Collectors

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.appendAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt
import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValuesAt


@Field static def VOLUNTEER_CONTACT_STATUS_RANGE = "\'Volunteer Contact Status\'!A:H"
@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.class)


static def getWaitlistForShiftRole(GoogleAPI googleAPI, sheetId, shiftRoleString){
    return getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE)
        .compose(volunteerStatusData->{
            def result = new ArrayList<WaitlistEntry>();
            def it = volunteerStatusData.iterator();

            while(it.hasNext()){
                def rowData = it.next()
                if(!rowData.isEmpty() && rowData.size() == 8 && rowData.get(7).equals(shiftRoleString) && rowData.get(2).equals("Waitlisted") && !rowData.get(6).equals("-")){
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
            return Future.succeededFuture(result)
        })
}

/**
 *
 * @param googleAPI
 * @param sheetId
 * @param volunteerEmail
 * @return true if the volunteer has already signed up for a different shift-role.
 */
static def hasVolunteerAlreadySignedUp(GoogleAPI googleAPI, sheetId, volunteerEmail){

    return getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE)
            .compose(volunteerStatusData->{
                //Get the row for the specified volunteer
                def volunteerRow = volunteerStatusData.stream().filter(rowData->!rowData.isEmpty() && rowData.get(0).equals(volunteerEmail)).findFirst().orElse(null);

                if (volunteerRow == null){
                    log.warn "Warning, could not find volunteer with email ${volunteerEmail} to check if they've already cancelled."
                    return Future.succeededFuture(true) //Going to return true anyways, to halt any operation being done.
                }

                return Future.succeededFuture(!volunteerRow.get(3).equals("-")) //Index 3 corresponds with 'Accepted On' in the sheet. If the value is something other than '-' they have already accepted a shift-role.

          })
}

/**
 *
 * @param googleAPI
 * @param sheetId
 * @param volunteerEmail
 * @return true if the volunteer has already cancelled a shift-role or rejected the opportunity entirely.
 */
static def hasVolunteerAlreadyCancelled(GoogleAPI googleAPI, sheetId, volunteerEmail){

    return getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE).compose{
        volunteerStatusData ->{
            //Get the row for the specified volunteer
            def volunteerRow = volunteerStatusData.stream().filter(rowData->!rowData.isEmpty() && rowData.get(0).equals(volunteerEmail)).findFirst().orElse(null);

            if (volunteerRow == null){
                log.warn "Warning, could not find volunteer with email ${volunteerEmail} to check if they've already cancelled."
                return Future.succeededFuture(true) //Gong to return true anyways, to halt any operation being done.
            }

            return Future.succeededFuture(!volunteerRow.get(5).equals("-") || !volunteerRow.get(4).equals("-")) //Index 5 corresponds with 'Cancelled On' in the sheet. Index 4 corresponds with 'Rejected On' If the value is something other than '-' they have already cancelled/rejected.
        }
    }

}

static def updateVolunteerStatus(GoogleAPI googleAPI, sheetId, volunteerEmail, volunteerStatus, shiftRoleString){

    return getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE).compose {
        volunteerStatusData ->
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
                            rowData.set(3, ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter))
                            break
                        case "Rejected":
                            rowData.set(4, ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter))
                            break
                        case "Cancelled":
                            rowData.set(5, ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter))
                            break
                        case "Waitlisted":
                            rowData.set(6, ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter))
                            break
                        case "Waiting for response":
                            rowData.set(1, ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter))
                            break
                        default:
                            log.warn "Unrecognized volunteer status string!"
                    }

                    break
                }
            }

            return updateVolunteerContactStatusTable(googleAPI, sheetId, volunteerStatusData)

    }


}

static Future<List<List<String>>> syncEventVolunteerContactSheet(GoogleAPI googleAPI, sheetId , wiserVolunteers){
    //wiserVolunteers is a set of all wiser volunteers.
    //Fetch Volunteer Contact Status data for this event
    log.info "Syncing volunteer contact sheet"
    return getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE).compose {
        volunteerContactStatusData ->
            try{
                log.info "got volunteer contact status data"
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
                def appendValues = makeNewVolunteerEntries(toAdd)
                valueRange.setValues(appendValues)
                valueRange.setMajorDimension("ROWS")

                if(appendValues.size() > 0){
                    log.info "Syncing ${appendValues.size()} volunteers."
                    //Append them into the volunteer contact status range
                    return appendAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE, valueRange ).compose {
                        //Get the updated volunteer contact status range
                        return getValuesAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE)
                    }
                }else{
                    log.info "No new volunteers to add."
                    return Future.succeededFuture(volunteerContactStatusData)
                }
            }catch(Exception e){
                log.error e.getMessage(), e
            }



    }

}

static def updateVolunteerContactStatusTable(GoogleAPI googleAPI, sheetId, data){
    def valueRange = new ValueRange()
    valueRange.setRange(VOLUNTEER_CONTACT_STATUS_RANGE)
    valueRange.setMajorDimension("ROWS")
    valueRange.setValues(data)
    return updateAt(googleAPI, sheetId, VOLUNTEER_CONTACT_STATUS_RANGE, valueRange )
}

private static makeNewVolunteerEntries(volunteers){
    def result = []
    volunteers.forEach{
        volunteer->
            result.add([volunteer, "-", "Not Contacted", "-", "-", "-","-"])
    }
    return result
}
