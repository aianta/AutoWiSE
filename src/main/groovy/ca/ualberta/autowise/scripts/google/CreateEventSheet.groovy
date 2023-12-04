package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.APICallContext
import ca.ualberta.autowise.model.Event
import com.google.api.services.drive.model.File
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.batchUpdate

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.CreateEventSheet.class)


/**
 * Makes a copy of the event sheet template and populates it with data from Event e.
 * @param config
 * @param googleAPI
 * @param sheetName The name of the sheet to create
 * @param e The event to populate the sheet with
 * @return
 */
def static createEventSheet(config, GoogleAPI googleAPI, String sheetName, Event e){



    return makeTemplateInstance(config, googleAPI, sheetName, config.getString("autowise_drive_folder_id")).compose{
        file->

            List<ValueRange> updates = generateSingleStaticUpdates(e)
            updates.add(generateHorizontalListUpdate(e.eventOrganizers, "Event!B7"))
            updates.add(generateHorizontalListUpdate(e.volunteerCoordinators, "Event!B8"))
            updates.add(new ValueRange().setRange(EventSlurper.ROLES_AND_SHIFTS_RANGE).setValues(generateRolesAndShiftsSection(config, e)))

            return batchUpdate(googleAPI, file.getId(), updates)
    }

}

private def static generateRolesAndShiftsSection(config, Event e){

    def result = [
            ["Roles", "Description"]
    ]

    def roleDescriptions = e.roles.stream().map(role->{
        return [role.name, role.description]
    }).collect(Collectors.toList());
    result.addAll(roleDescriptions)

    if(roleDescriptions.size() < config.getInteger("max_roles_per_event")){
        int numEmptyRows = config.getInteger("max_roles_per_event") - roleDescriptions.size()
        while(numEmptyRows > 0){
            result.add([])
            numEmptyRows--
        }
    }

    result.add(["Shifts","","",""]) //Shifts header

    def shifts = e.roles.stream().map(role->{
        // Create Shift Header
        def shiftHeader = [role.name, "Start", "End", "Volunteers per shift"]
        def shiftResult = [shiftHeader]
        def shifts = role.shifts.stream()
                .map(shift->{
                   return [
                           shift.index,
                           shift.startTime.format(EventSlurper.shiftTimeFormatter),
                           shift.endTime.format(EventSlurper.shiftTimeFormatter),
                           shift.targetNumberOfVolunteers
                   ]
                })
        .forEach(s->shiftResult.add(s))


        shiftResult.add([])
        return shiftResult
    }).forEach(shift->{result.addAll(shift)})


    return result
}

private def static generateHorizontalListUpdate(List values, String range){
    return new ValueRange().setRange(range)
            .setMajorDimension("ROWS")
            .setValues([values])
}

private def static generateSingleStaticUpdates(Event e){

    List<ValueRange> result = new ArrayList<>();

    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("id")).setValues([[e.id.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("status")).setValues([[e.status.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("name")).setValues([[e.name.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("description")).setValues([[e.description.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("eventStartTime")).setValues([[e.startTime.format(EventSlurper.eventTimeFormatter)]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("eventEndTime")).setValues([[e.endTime.format(EventSlurper.eventTimeFormatter)]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("eventbriteLink")).setValues([[e.eventbriteLink.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("eventSlackChannel")).setValues([[e.eventSlackChannel.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("campaignStartOffset")).setValues([[e.campaignStart.format(EventSlurper.eventTimeFormatter)]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("resolicitFrequency")).setValues([[TimeUnit.MILLISECONDS.toDays(e.resolicitFrequency).toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("followupOffset")).setValues([[e.followupTime.format(EventSlurper.eventTimeFormatter)]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("initialRecruitmentEmailTemplateId")).setValues([[e.initialRecruitmentEmailTemplateId.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("recruitmentEmailTemplateId")).setValues([[e.recruitmentEmailTemplateId.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("followupEmailTemplateId")).setValues([[e.followupEmailTemplateId.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("confirmAssignedEmailTemplateId")).setValues([[e.confirmAssignedEmailTemplateId.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("confirmCancelledEmailTemplateId")).setValues([[e.confirmCancelledEmailTemplateId.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("confirmWaitlistEmailTemplateId")).setValues([[e.confirmWaitlistEmailTemplateId.toString()]]))
    result.add(new ValueRange().setRange(EventSlurper.staticSingleValues.get("confirmRejectedEmailTemplateId")).setValues([[e.confirmRejectedEmailTemplateId.toString()]]))

    return result;
}

private def static makeTemplateInstance(config, GoogleAPI googleAPI, String sheetName, String parentFolderId){
    def templateId = config.getString("autowise_event_template_sheet")
    APICallContext context = new APICallContext()
    context.put("note", "Creating a new sheet by copying template sheet.")
    context.put("templateId", templateId)
    context.put("newSheetName", sheetName)
    context.put("parentFolderId", parentFolderId)

    return googleAPI.<File>drive(context, {it.files().copy(templateId, new File().setName(sheetName).setParents([parentFolderId]))})
}

/**
 * TODO: actually create the sheet from scratch to avoid sky falling if the template is deleted/modified/etc.
 *    deferring for now because configuring all the editable areas and whatnot is a pain.
 * @param googleAPI
 * @param sheetName
 * @param parentFolderId
 * @return
 */
private def static createBlankSheet(GoogleAPI googleAPI, String sheetName, String parentFolderId){

    APICallContext context = new APICallContext();
    context.put "newSheetName", sheetName
    context.put "parentFolderId", parentFolderId
    context.put "note", "creating a blank event spreadsheet"

    File fileMetadata = new File();
    fileMetadata.setName(sheetName);
    fileMetadata.setMimeType("application/vnd.google-apps.spreadsheet");
    fileMetadata.setParents([parentFolderId])


    return googleAPI.<File>drive(context, {it.files().create(fileMetadata).setFields("id")})

}