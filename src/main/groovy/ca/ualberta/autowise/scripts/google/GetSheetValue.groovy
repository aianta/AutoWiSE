package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.APICallContext
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.core.Future
import io.vertx.core.Promise
import org.slf4j.LoggerFactory


@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.GetSheetValue.class)

/**
 *
 * @param googleAPI the google api object with which to make the call
 * @param sheetId the id of the spreadsheet in which the values are located
 * @param range the range in the spreadsheet where the values are located
 * @return A future that succeeds, returning the values at the specified range. The future will fail if no values could be found. Values will be in the form of a 2D list.
 */
static def getValuesAt(GoogleAPI googleAPI, sheetId, range){

    APICallContext context = new APICallContext()
    context.put "note", "retrieving values at ${range} from sheet: ${sheetId}"
    context.sheetId(sheetId)
    context.cellAddress(range)

    return googleAPI.<ValueRange>sheets(context, {it.spreadsheets().values().get(sheetId, range)})
        .compose {response->
            def value = response.getValues()
            if (value == null || value.isEmpty()){
                log.error "No value found in sheet ${sheetId}@${range}!"
                return Future.failedFuture("No value found in sheet ${sheetId}@${range}!")
            }
            return Future.succeededFuture(value)
        }
}

static def getValueAt(GoogleAPI googleAPI, sheetId, cellAddress){

    APICallContext context = new APICallContext()
    context.put "note", "retrieving single value at ${cellAddress} from sheet: ${sheetId}"
    context.sheetId(sheetId)
    context.cellAddress(cellAddress)

    return googleAPI.<ValueRange>sheets(context, {it.spreadsheets().values().get(sheetId, cellAddress)})
        .compose {response->
            def value = response.getValues()
            if (value == null || value.isEmpty()){
                log.error "No value found in sheet ${sheetId}@${cellAddress}!"
                return Future.failedFuture("No value found in sheet ${sheetId}@${cellAddress}!")
            }
            return Future.succeededFuture(value.get(0).get(0))
        }

}