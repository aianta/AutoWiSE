package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.SQLite
import ca.ualberta.autowise.model.ShiftAssignment
import com.google.api.services.sheets.v4.model.ValueRange

import java.lang.module.FindException

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt


static def updateEventStatusTable(GoogleAPI googleAPI, sheetId, data){
    def valueRange = new ValueRange()
    valueRange.setRange(FindAvailableShiftRoles.EVENT_STATUS_RANGE)
    valueRange.setValues(data)
    valueRange.setMajorDimension("ROWS")
    return updateAt(googleAPI, sheetId,FindAvailableShiftRoles.EVENT_STATUS_RANGE, valueRange)
}

static def updateEventStatusTable(services, sheetId){

    return ((SQLite)services.db).getAllShiftRoles(sheetId).compose {

        List<List<String>> eventStatusData = new ArrayList<>();
        //Construct table values
        eventStatusData.add(makeHeaderRow())

        it.forEach {row->
            eventStatusData.add(makeRowEntry(row))
        }

        ValueRange valueRange = new ValueRange();
        valueRange.setRange(FindAvailableShiftRoles.EVENT_STATUS_RANGE)
        valueRange.setValues(eventStatusData)
        valueRange.setMajorDimension("ROWS")

        return updateAt(services.googleAPI, sheetId, FindAvailableShiftRoles.EVENT_STATUS_RANGE, valueRange )

    }

}

private static def makeHeaderRow(){
    return ["Shift - Role", "Volunteer", "Name"]
}

private static def makeRowEntry(ShiftAssignment data){
    return [data.shiftRole,
            data.volunteerEmail == null?"":data.volunteerEmail,
            data.volunteerName == null?"":data.volunteerName
    ]
}
