package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.json.JsonOutput
import groovy.transform.Field
import org.slf4j.LoggerFactory

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

@Field static GoogleAPI api
@Field static sheetId
@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.EventSlurper.class)
@Field static ROLES_AND_SHIFTS_RANGE = "Event!A23:D200"

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

/**
 * @Author Alexandru Ianta
 * Parse a google sheet as an event description and
 * produce an {@link ca.ualberta.autowise.model.Event}
 */

static def slurpSheet(GoogleAPI googleAPI, spreadsheetId){
    api = googleAPI
    sheetId = spreadsheetId

    def staticSingleValues = [
            "id": "Event!A2",
            "status":"Event!A3",
            "name": "Event!B5",
            "description":"Event!B6",
            "eventStartTime": "Event!B9",
            "eventEndTime": "Event!B10",
            "eventbriteLink": "Event!B11",
            "eventSlackChannel": "Event!B12",
            "executiveRatio": "Event!B15",
            "campaignStartOffset": "Event!B16",
            "resolicitFrequency": "Event!B17",
            "followupOffset": "Event!B18",
            "recruitmentEmailTemplateId": "Event!B19",
            "followupEmailTemplateId": "Event!B20",
            "confirmAssignedEmailTemplateId": "Event!B21",
            "confirmCancelledEmailTemplateId": "Event!B22",
            "confirmWaitlistEmailTemplateId": "Event!B23",
            "confirmRejectedEmailTemplateId": "Event!B24",
    ]

    def slurped = slurpStaticSingleValues(staticSingleValues)
    slurped.forEach {key,value-> log.info "${key}: ${value}"}

    List<Role> roles = slurpRolesAndShifts()

    Event result = new Event(
            id: slurped.get("id") == null?null:UUID.fromString(slurped.get("id")),
            status: EventStatus.valueOf(slurped.get("status")),
            name: slurped.get("name"),
            description: slurped.get("description"),
            startTime: ZonedDateTime.parse(slurped.get("eventStartTime"), eventTimeFormatter),
            endTime: ZonedDateTime.parse(slurped.get("eventEndTime"), eventTimeFormatter),
            eventbriteLink: slurped.get("eventbriteLink"),
            eventSlackChannel: slurped.get("eventSlackChannel"),
            eventOrganizers: slurpEmailsHorizontally("Event!7:7"),
            volunteerCoordinators: slurpEmailsHorizontally("Event!8:8"),
            executiveRatio: Double.parseDouble(slurped.get("executiveRatio")),
            campaignStartOffset: 1000 * 60 * 60 * 24 * Long.parseLong(slurped.get("campaignStartOffset")), //Convert days to ms
            resolicitFrequency: 1000 * 60 * 60 * 24 * Long.parseLong(slurped.get("resolicitFrequency")),   //Convert days to ms
            followupOffset: 1000 * 60 * 60 * Long.parseLong(slurped.get("followupOffset")),                //Convert hours to ms
            recruitmentEmailTemplateId: slurped.get("recruitmentEmailTemplateId"),
            followupEmailTemplateId: slurped.get("followupEmailTemplateId"),
            confirmAssignedEmailTemplateId: slurped.get("confirmAssignedEmailTemplateId"),
            confirmCancelledEmailTemplateId: slurped.get("confirmCancelledEmailTemplateId"),
            confrimWaitlistEmailTemplateId: slurped.get("confirmWaitlistEmailTemplateId"),
            confirmRejectedEmailTemplateId: slurped.get("confirmRejectedEmailTemplateId"),
            roles: roles
    )


    return result
}

private static def mapValuesToList(LinkedHashMap map){
    List<String> result = new ArrayList<>();
    map.forEach { key,value-> result.add(value) }
    return result
}

private static def slurpStaticSingleValues(LinkedHashMap values){
    //Create a new map storing the slurped results.
    def slurped = new LinkedHashMap<String,String>()

    try{
        def response = api.sheets().spreadsheets().values().batchGet(sheetId).setRanges(mapValuesToList(values)).execute()

        //Walk through the ranges and the initial value map one by one to create the slurped map.
        ListIterator<ValueRange> it = response.getValueRanges().listIterator()
        Iterator<Map.Entry<String,String>> valuesIt = values.iterator()
        //These two maps should be of the same size, freak out if not.
        if (values.size() != response.getValueRanges().size()){
            throw new RuntimeException("Got ${response.getValueRanges().size()} values back but expected ${values.size()} when slurping event data from sheet:${sheetId}")
        }

        while (it.hasNext()){
            int index = it.nextIndex();
            ValueRange curr = it.next();
            slurped.put(valuesIt.next().getKey(), curr.getValues() == null?null:curr.getValues().get(0).get(0) )
        }

    }catch (GoogleJsonResponseException e){
        GoogleJsonError error = e.getDetails()
        log.error error.getMessage()
        throw e
    }

    return slurped
}

/**
 * @param range can only process row ranges IE: (7:7) or (8:8)
 * @return A list of all values on that row that contain '@'
 */
private static def slurpEmailsHorizontally(range){
    List<String> emails = new ArrayList<String>()
    try{
        def response = api.sheets().spreadsheets().values().get(sheetId, range).execute()
        def data = response.getValues();

        if (data == null || data.isEmpty()){
            throw new RuntimeException("Could not slurp emails horizontally at ${range} for sheet: ${sheetId}")
        }else{
            //These are single row values so we'll unwrap the row list
            def rowData = data.get(0)

            emails = rowData.stream().filter(cell -> cell.contains('@')).collect(Collectors.toList())

        }
    }catch (GoogleJsonResponseException e){
        GoogleJsonError error = e.getDetails()
        log.error error.getMessage()
        throw e
    }

    return emails;

}


private static def slurpRolesAndShifts(){
    //Make a list of roles to store the result
    List<Role> roles = new ArrayList()

    try{
        def response = api.sheets().spreadsheets().values().get(sheetId, ROLES_AND_SHIFTS_RANGE).execute()
        def data = response.getValues();
        if(data == null || data.isEmpty()){
            throw new RuntimeException("Could not find role and shift information in ${sheetId}")
        }else{

            ListIterator rowIt = data.listIterator();
            while (rowIt.hasNext()){
                def rowIndex = rowIt.nextIndex();
                def rowData = rowIt.next();
                log.info "row [${rowIndex}]: ${rowData.toListString()}"

                if (rowData.isEmpty()){
                    continue;
                }


                switch (rowData.get(0)){
                    case "Roles": //Roles header
                        rowIndex = rowIt.nextIndex()
                        rowData = rowIt.next()
                        while (!rowData.isEmpty() && !rowData.get(0).equals("Shifts")){
                            Role r = new Role(
                                    name: rowData.get(0),
                                    description: rowData.get(1)
                            )
                            roles.add(r)
                            rowIndex = rowIt.nextIndex()
                            rowData = rowIt.next()
                        }
                        //Back-up one row to allow next if we went all the way to shifts, so that the switch clause can do the processing appropriately.
                        if (!rowData.isEmpty() && rowData.get(0).equals("Shifts")){
                            rowIndex = rowIt.previousIndex()
                            rowData = rowIt.previous()
                        }
                        break;
                    case "Shifts": //Shifts header
                        rowIndex = rowIt.nextIndex()
                        rowData = rowIt.next()

                        do{
                            Role r = getRoleByName(roles, rowData.get(0))



                            rowIndex = rowIt.nextIndex()
                            rowData = rowIt.next()
                            while(!rowData.isEmpty() && rowData.get(0).matches("\\d+")){ //While the first column contains a number.
                                Shift s = new Shift(
                                        index: Integer.parseInt(rowData.get(0)),
                                        startTime: LocalTime.parse(rowData.get(1), shiftTimeFormatter),
                                        endTime: LocalTime.parse(rowData.get(2), shiftTimeFormatter),
                                        targetNumberOfVolunteers: Integer.parseInt(rowData.get(3))
                                )
                                r.shifts.add(s)

                                if (rowIt.hasNext()){
                                    rowIndex = rowIt.nextIndex()
                                    rowData = rowIt.next()
                                }else{
                                    break;
                                }

                            }

                            //Skip blank rows between roles
                            while(rowIt.hasNext() && rowData.isEmpty()){
                                rowIndex = rowIt.nextIndex()
                                rowData = rowIt.next()
                            }

                        }while(rowIt.hasNext() && getRoleByName(roles, rowData.get(0))!= null)



                        break;
                    case "": //empty cell

                        break;
                }

            }

        }

    }catch (GoogleJsonResponseException e){
        GoogleJsonError error = e.getDetails()
        log.error error.getMessage()
        throw e
    }

    return roles
}

private static def getRoleByName(List<Role> roles, String name){
    return roles.stream().filter(r->r.name.equals(name)).findFirst().orElse(null);
}