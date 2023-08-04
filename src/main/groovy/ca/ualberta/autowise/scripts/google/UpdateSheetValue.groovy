package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import com.google.api.services.sheets.v4.model.UpdateValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.core.Promise
import org.slf4j.LoggerFactory

import java.util.stream.Collectors

/**
 * @author Alexandru Ianta
 * Helper script for updating a single value in a google sheet.
 */

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.UpdateSheetValue.class)

/**
 * @param googleAPI GoogleAPI object to use when making the request.
 * @param sheetId The id of the sheet containing the address to be updated
 * @param cellAddress The address to be updated in the sheet.
 * @param value The value to be placed at that address
 */
def static updateSingleValueAt(GoogleAPI googleAPI, sheetId, cellAddress, value){

    def bodyContent = [[value]]
    ValueRange body = new ValueRange()
            .setValues(bodyContent)
    return updateAt(googleAPI, sheetId, cellAddress, body)
}

def static updateRowValueAt(GoogleAPI googleAPI, sheetId, cellAddress, Collection<String> data){

        data = data.stream()
        /**
         *        Because we're in groovy, our strings are often GStrings instead of actual strings.
         *        Google's API would however be very displeased to get a GString when it just wants
         *        strings.
         *        So we convert any GString to Strings with this mapping step
         */
                .map(element->element.toString())
                .collect(Collectors.toList())
        data = [data]
        ValueRange body = new ValueRange()
                .setRange(cellAddress)
                .setValues(data)
                .setMajorDimension("ROWS")
        return updateAt(googleAPI, sheetId, cellAddress, body)

}

/**
 *
 * @param googleAPI GoogleAPI object to use when making the request.
 * @param sheetId The id of the sheet containing the address to be updated
 * @param cellAddress The address to be updated in the sheet, values will start here and appear below in a vertical stack
 * @param values The values to enter
 */
def static updateColumnValueAt(GoogleAPI googleAPI, sheetId, cellAddress, List<String> data){
   try{

       data = data.stream()
       /**
        *        Because we're in groovy, our strings are often GStrings instead of actual strings.
        *        Google's API would however be very displeased to get a GString when it just wants
        *        strings.
        *        So we convert any GString to Strings with this mapping step
        */
               .map(element->element.toString())
               .collect(Collectors.toList())
       data = [data]
       ValueRange body = new ValueRange()
               .setRange(cellAddress)
               .setValues(data)
               .setMajorDimension("COLUMNS")
       return updateAt(googleAPI, sheetId, cellAddress, body)

   }catch (Exception e){
       log.error e.getMessage(), e
   }
}

static def appendAt(GoogleAPI googleAPI, sheetId, cellAddress, body){
    Promise promise = Promise.promise()
    try{
        AppendValuesResponse response = googleAPI.sheets().spreadsheets().values().append(sheetId, cellAddress, body)
                .setValueInputOption("RAW")
                .setIncludeValuesInResponse(true)
                .execute()
        log.info response.toPrettyString()
        promise.complete()
    }catch (GoogleJsonResponseException | Exception e) {
        // TODO(developer) - handle error appropriately
        //GoogleJsonError error = e.getDetails();
        log.error e.getMessage(), e
        promise.fail(e)
    }
    return promise.future()
}

static def updateAt(GoogleAPI googleAPI, sheetId, cellAddress, body){
    Promise promise = Promise.promise()
    try{
        UpdateValuesResponse response = googleAPI.sheets().spreadsheets().values().update(sheetId, cellAddress, body)
                .setValueInputOption("RAW") // See: https://developers.google.com/sheets/api/reference/rest/v4/ValueInputOption
                .setIncludeValuesInResponse(true)
                .execute()

        log.info response.toPrettyString()
        promise.complete()
    } catch (GoogleJsonResponseException | Exception e) {
        log.error "Error updating value at ${sheetId}@${cellAddress}!"
        // TODO(developer) - handle error appropriately
        //GoogleJsonError error = e.getDetails();
        log.error e.getMessage(), e
        promise.fail(e)
    }
    return promise.future()
}

