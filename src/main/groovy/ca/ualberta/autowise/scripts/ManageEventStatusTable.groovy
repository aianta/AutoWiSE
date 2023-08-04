package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import com.google.api.services.sheets.v4.model.ValueRange

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt


static def updateEventStatusTable(GoogleAPI googleAPI, sheetId, data){
    def valueRange = new ValueRange()
    valueRange.setRange(FindAvailableShiftRoles.EVENT_STATUS_RANGE)
    valueRange.setValues(data)
    valueRange.setMajorDimension("ROWS")
    return updateAt(googleAPI, sheetId,FindAvailableShiftRoles.EVENT_STATUS_RANGE, valueRange)
}

