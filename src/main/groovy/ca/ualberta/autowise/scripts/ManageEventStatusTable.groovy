package ca.ualberta.autowise.scripts


import ca.ualberta.autowise.SQLite
import ca.ualberta.autowise.model.ShiftAssignment
import com.google.api.services.sheets.v4.model.ValueRange
import io.vertx.core.Future

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt


static def updateEventStatusTable(services, sheetId){

    return generateEventStatusTable(services, sheetId).compose {
        return updateAt(services.googleAPI, sheetId, FindAvailableShiftRoles.EVENT_STATUS_RANGE, it)
    }

}

static def generateEventStatusTable(services, sheetId){
    return ((SQLite)services.db).getAllShiftRoles(sheetId).compose {

        List<List<String>> eventStatusData = new ArrayList<>();
        //Construct table values
        //Make the header row
        eventStatusData.add(["Shift - Role", "Volunteer", "Name"])
        //Fill in the data
        it.forEach { row ->
            eventStatusData.add(makeRowEntry(row))
        }

        ValueRange valueRange = new ValueRange();
        valueRange.setRange(FindAvailableShiftRoles.EVENT_STATUS_RANGE)
        valueRange.setValues(eventStatusData)
        valueRange.setMajorDimension("ROWS")

        return Future.succeededFuture(valueRange)
    }

}


private static def makeRowEntry(ShiftAssignment data){
    return [data.shiftRole,
            data.volunteerEmail == null?"":data.volunteerEmail,
            data.volunteerName == null?"":data.volunteerName
    ]
}
