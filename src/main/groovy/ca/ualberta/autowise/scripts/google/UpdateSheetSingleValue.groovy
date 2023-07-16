package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import org.slf4j.LoggerFactory

/**
 * @author Alexandru Ianta
 * Helper script for updating a single value in a google sheet.
 */

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.UpdateSheetSingleValue.class)

/**
 * @param googleAPI GoogleAPI object to use when making the request.
 * @param sheetId The id of the sheet containing the value to be updated
 * @param cellAddress The address to be updated in the sheet.
 * @param value The value to be placed at that address
 */
def static updateSingleValueAt(GoogleAPI googleAPI, sheetId, cellAddress, value){

    def bodyContent = [[value]]

    def result
    try{
        ValueRange body = new ValueRange()
            .setValues(bodyContent)
        result = googleAPI.sheets().spreadsheets().values().update(sheetId, cellAddress, body)
                .setValueInputOption("RAW") // See: https://developers.google.com/sheets/api/reference/rest/v4/ValueInputOption
                .execute()

        log.info "Updated sheet ${sheetId}@${cellAddress} with value ${value}"
    }catch (GoogleJsonResponseException e) {
        // TODO(developer) - handle error appropriately
        GoogleJsonError error = e.getDetails();
        log.error e.getMessage(), e
        throw e
    }

}

