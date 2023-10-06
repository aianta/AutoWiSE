package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.APICallContext
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.UpdateValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.core.Future
import io.vertx.core.Promise
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValuesAt

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

static def clearRange(GoogleAPI googleAPI, sheetId, cellAddress){
    return getValuesAt(googleAPI, sheetId, cellAddress).compose(
        values->{
            def rowIt = values.iterator()
            while (rowIt.hasNext()){
                def row = (List<Object>)rowIt.next()
                for(int i = 0; i < row.size(); i++){
                    row.set(i, "")
                }
            }
            ValueRange body = new ValueRange()
                    .setRange(cellAddress)
                    .setValues(values)
                    .setMajorDimension("ROWS")
            return updateAt(googleAPI, sheetId, cellAddress, body)

    }, err->{
        //If the error is that there aren't values at the ranges we're trying to clear, then we're set!
        if (err.getMessage().contains("No value found")){
            return Future.succeededFuture()
        }else{
            return Future.failedFuture(err)
        }
    })
}

static def appendAt(GoogleAPI googleAPI, sheetId, cellAddress, ValueRange body){
    APICallContext context = new APICallContext()
    context.put "note", "Appending values to sheet ${sheetId}@${cellAddress}"
    context.sheetId(sheetId)
    context.cellAddress(cellAddress)
    context.valueRange(body)

    return googleAPI.<AppendValuesResponse>sheets(context, {it.spreadsheets().values().append(sheetId, cellAddress, body).setValueInputOption("RAW")})
}

static def updateAt(GoogleAPI googleAPI, sheetId, cellAddress, body){
    APICallContext context = new APICallContext()
    context.put "note", "Updating values to sheet ${sheetId}@${cellAddress}"
    context.sheetId(sheetId)
    context.cellAddress(cellAddress)
    context.valueRange(body)

    return googleAPI.<UpdateValuesResponse>sheets(context, {it.spreadsheets().values().update(sheetId, cellAddress, body).setValueInputOption("RAW")})
}

static def batchUpdate(GoogleAPI googleAPI, sheetId,  List<ValueRange> updates){

    APICallContext context = new APICallContext()
    context.sheetId(sheetId)
    context.put "note", "Performing batch update to sheet ${sheetId}."

    BatchUpdateValuesRequest request = new BatchUpdateValuesRequest()
            .setValueInputOption("RAW")
            .setData(updates)

    return googleAPI.<BatchUpdateValuesRequest>sheets(context, {it.spreadsheets().values().batchUpdate(sheetId, request)})
        .compose {
            log.info "Updated ${it.getTotalUpdatedCells().toString()} cells in ${sheetId}"
            return Future.succeededFuture(sheetId) //Return the id of the updated sheet.
        }
}