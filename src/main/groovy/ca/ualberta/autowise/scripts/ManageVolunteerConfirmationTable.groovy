package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.SQLite
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.core.Future
import org.slf4j.LoggerFactory



import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.ManageVolunteerConfirmationTable.class)
@Field static VOLUNTEER_CONFIRMATION_RANGE = "\'Volunteer Confirmations\'!A:D" //TODO -> move to a constants file? See about testing

static def updateVolunteerConfirmationTable(services, sheetId){

    return generateVolunteerConfirmationTable(services, sheetId).compose {
        return updateAt(services.googleAPI, sheetId, VOLUNTEER_CONFIRMATION_RANGE, it)
    }

}

static def generateVolunteerConfirmationTable(services, sheetId){

    return ((SQLite)services.db).getVolunteerConfirmations(sheetId).compose {

        List<List<String>> tableData = new ArrayList<>();

        tableData.add(["Email", "Name", "Shift - Role", "Confirmation Received on"])

        it.forEach { volunteerConfirmation ->
            tableData.add([
                    volunteerConfirmation.volunteerEmail,
                    volunteerConfirmation.volunteerName,
                    volunteerConfirmation.shiftRole,
                    volunteerConfirmation.timestamp == null ? "" : volunteerConfirmation.timestamp.format(EventSlurper.eventTimeFormatter)
            ])
        }

        ValueRange valueRange = new ValueRange();
        valueRange.setRange(VOLUNTEER_CONFIRMATION_RANGE)
        valueRange.setValues(tableData)
        valueRange.setMajorDimension("ROWS")

        return Future.succeededFuture(valueRange)
    }

}



