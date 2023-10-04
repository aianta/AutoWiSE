package ca.ualberta.autowise.scripts.google

/**
 * @Author Alexandru Ianta
 * Parse a google sheet as an event description and
 * produce an {@link ca.ualberta.autowise.model.Event}
 */

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.APICallContext
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValuesAt

@Field static GoogleAPI api
@Field static sheetId
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

@Field static def dynamicRanges = [
        "eventOrganizers": "Event!7:7",
        "volunteerCoordinators": "Event!8:8",
        "roles": ROLES_AND_SHIFTS_RANGE
]

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

static def slurpSheet(GoogleAPI googleAPI, spreadsheetId){
    api = googleAPI
    sheetId = spreadsheetId



    return CompositeFuture.all(
            slurpStaticSingleValues(staticSingleValues),
            slurpRolesAndShifts(),
            slurpEmailsHorizontally(dynamicRanges.get("eventOrganizers")),
            slurpEmailsHorizontally(dynamicRanges.get("volunteerCoordinators"))
    ).compose{
        composite->
            def slurped = composite.resultAt(0)
            List<Role> roles = composite.resultAt(1)
            List<String> eventOrganizers = composite.resultAt(2)
            List<String> volunteerCoordinators = composite.resultAt(3)


            Event result = new Event(
                    id: slurped.get("id") == null?null:UUID.fromString(slurped.get("id")),
                    sheetId: sheetId,
                    status: EventStatus.valueOf(slurped.get("status")),
                    name: slurped.get("name"),
                    description: slurped.get("description"),
                    startTime: ZonedDateTime.parse(slurped.get("eventStartTime"), eventTimeFormatter),
                    endTime: ZonedDateTime.parse(slurped.get("eventEndTime"), eventTimeFormatter),
                    eventbriteLink: slurped.get("eventbriteLink"),
                    eventSlackChannel: slurped.get("eventSlackChannel"),
                    eventOrganizers: eventOrganizers,
                    volunteerCoordinators: volunteerCoordinators,
                    campaignStartOffset: Duration.ofDays(Long.parseLong(slurped.get("campaignStartOffset"))).toMillis(), //Convert days to ms
                    resolicitFrequency: Duration.ofDays(Long.parseLong(slurped.get("resolicitFrequency"))).toMillis(),   //Convert days to ms
                    followupOffset: Duration.ofHours(Long.parseLong(slurped.get("followupOffset"))).toMillis(),          //Convert hours to ms
                    initialRecruitmentEmailTemplateId: slurped.get("initialRecruitmentEmailTemplateId"),
                    recruitmentEmailTemplateId: slurped.get("recruitmentEmailTemplateId"),
                    followupEmailTemplateId: slurped.get("followupEmailTemplateId"),
                    confirmAssignedEmailTemplateId: slurped.get("confirmAssignedEmailTemplateId"),
                    confirmCancelledEmailTemplateId: slurped.get("confirmCancelledEmailTemplateId"),
                    confirmWaitlistEmailTemplateId: slurped.get("confirmWaitlistEmailTemplateId"),
                    confirmRejectedEmailTemplateId: slurped.get("confirmRejectedEmailTemplateId"),
                    roles: roles
            )


            return Future.succeededFuture(result)
    }


}

private static def mapValuesToList(LinkedHashMap map){
    List<String> result = new ArrayList<>();
    map.forEach { key,value-> result.add(value) }
    return result
}

private static def slurpStaticSingleValues(LinkedHashMap values){

    //Create a new map storing the slurped results.
    def slurped = new LinkedHashMap<String,String>()


    APICallContext context = new APICallContext();
    context.sheetId(sheetId)
    context.cellAddresses(mapValuesToList(values))
    context.put "note", "reading static values from event spreadsheet"

    return api.<BatchGetValuesResponse>sheets(context, {it.spreadsheets().values().batchGet(sheetId).setRanges(mapValuesToList(values))})
        .compose {response->

            //Walk through the ranges and the initial value map one by one to create the slurped map.
            ListIterator<ValueRange> it = response.getValueRanges().listIterator()
            Iterator<Map.Entry<String,String>> valuesIt = values.iterator()
            //These two maps should be of the same size, freak out if not.
            if (values.size() != response.getValueRanges().size()){
                promise.fail("Got ${response.getValueRanges().size()} values back but expected ${values.size()} when slurping event data from sheet:${sheetId}")
                throw new RuntimeException("Got ${response.getValueRanges().size()} values back but expected ${values.size()} when slurping event data from sheet:${sheetId}")

            }

            while (it.hasNext()){
                ValueRange curr = it.next();
                slurped.put(valuesIt.next().getKey(), curr.getValues() == null?null:curr.getValues().get(0).get(0) )
            }

            return Future.succeededFuture(slurped)
        }


}

/**
 * @param range can only process row ranges IE: (7:7) or (8:8)
 * @return A list of all values on that row that contain '@'
 */
private static def slurpEmailsHorizontally(range){
    List<String> emails = new ArrayList<String>()

    APICallContext context = new APICallContext()
    context.sheetId(sheetId)
    context.cellAddress(range)
    context.put "note",  "reading event organizer or volunteer coordinator emails horizontally row-wise from the event spreadsheet."

    return api.<ValueRange>sheets(context, {it.spreadsheets().values().get(sheetId, range)})
        .compose {response->
            def data = response.getValues();

            if (data == null || data.isEmpty()){
                throw new RuntimeException("Could not slurp emails horizontally at ${range} for sheet: ${sheetId}")
            }else{
                //These are single row values so we'll unwrap the row list
                def rowData = data.get(0)

                emails = rowData.stream().filter(cell -> cell.contains('@')).collect(Collectors.toList())

            }
            return Future.succeededFuture(emails)
        }
}


private static def slurpRolesAndShifts(){


    return getValuesAt(api, sheetId, ROLES_AND_SHIFTS_RANGE).compose{
        data->
            //Make a list of roles to store the result
            List<Role> roles = new ArrayList()

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
                        log.info "${rowData}"
                        rowData = rowIt.next()
                        while (!rowData.isEmpty() && !rowData.get(0).equals("Shifts")){
                            Role r = new Role(
                                    name: rowData.get(0),
                                    description: rowData.get(1)
                            )
                            roles.add(r)
                            rowData = rowIt.next()
                        }
                        //Back-up one row to allow next if we went all the way to shifts, so that the switch clause can do the processing appropriately.
                        if (!rowData.isEmpty() && rowData.get(0).equals("Shifts")){

                            rowData = rowIt.previous()
                        }
                        break;
                    case "Shifts": //Shifts header

                        rowData = rowIt.next()

                        do{
                            Role r = getRoleByName(roles, rowData.get(0))



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

                                    rowData = rowIt.next()
                                }else{
                                    break;
                                }

                            }

                            //Skip blank rows between roles
                            while(rowIt.hasNext() && rowData.isEmpty()){

                                rowData = rowIt.next()
                            }

                        }while(rowIt.hasNext() && getRoleByName(roles, rowData.get(0))!= null)



                        break;
                    case "": //empty cell

                        break;
                }

            }

            return Future.succeededFuture(roles)
    }



}

private static def getRoleByName(List<Role> roles, String name){
    return roles.stream().filter(r->r.name.equals(name)).findFirst().orElse(null);
}

/**
 * Returns true for all statuses an event can be in and still be slurped
 * @param status
 */
static def isSlurpable(status){

    def slurpableStatuses = [
            EventStatus.READY.toString(),
            EventStatus.IN_PROGRESS.toString()
    ] as Set

    return slurpableStatuses.contains(status)
}