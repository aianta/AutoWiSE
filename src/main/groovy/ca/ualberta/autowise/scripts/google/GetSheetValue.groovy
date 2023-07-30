package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import groovy.transform.Field
import org.slf4j.LoggerFactory


@Field static log = LoggerFactory.getLogger()

static def getValuesAt(GoogleAPI googleAPI, sheetId, range){
    def response = googleAPI.sheets().spreadsheets().values().get(sheetId, range).execute()
    def value = response.getValues()
    if (value == null || value.isEmpty()){
        log.error "No value found in sheet ${sheetId}@${range}!"
    }else{
        return value
    }
    return null
}

public static def getValueAt(GoogleAPI googleAPI, sheetId, cellAddress){
    def response = googleAPI.sheets().spreadsheets().values().get(sheetId, cellAddress).execute()
    def value = response.getValues()
    if (value == null || value.isEmpty()){
        log.error "No value found in sheet ${sheetId}@${cellAddress}!"
    }else{
        return value.get(0).get(0)
    }
    return null
}