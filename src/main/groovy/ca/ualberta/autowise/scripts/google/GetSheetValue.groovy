package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import groovy.transform.Field
import io.vertx.core.Promise
import org.slf4j.LoggerFactory


@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.GetSheetValue.class)

static def getValuesAt(GoogleAPI googleAPI, sheetId, range){
    Promise promise = Promise.promise();
    try{
        def response = googleAPI.sheets().spreadsheets().values().get(sheetId, range).execute()
        def value = response.getValues()
        if (value == null || value.isEmpty()){
            log.error "No value found in sheet ${sheetId}@${range}!"
            promise.fail("No value found in sheet ${sheetId}@${range}!")
        }else{
            promise.complete(value)
        }
    }catch(GoogleJsonResponseException | Exception e){

        log.error "Error getting data from ${sheetId}@${range}"

        GoogleJsonError error = e.getDetails();
        log.error e.getMessage(), e
        promise.fail(e)
    }

    return promise.future()
}

static def getValueAt(GoogleAPI googleAPI, sheetId, cellAddress){
    Promise promise = Promise.promise()
    try{
        def response = googleAPI.sheets().spreadsheets().values().get(sheetId, cellAddress).execute()
        def value = response.getValues()
        if (value == null || value.isEmpty()){
            log.error "No value found in sheet ${sheetId}@${cellAddress}!"
            promise.fail("No value found in sheet ${sheetId}@${cellAddress}!")
        }else{
            promise.complete(value.get(0).get(0));
        }
    }catch(GoogleJsonResponseException e){
        GoogleJsonError error = e.getDetails();
        log.error e.getMessage(), e
        promise.fail(e)
    }

    return promise.future()
}