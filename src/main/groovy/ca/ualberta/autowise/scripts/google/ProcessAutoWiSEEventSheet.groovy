package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI

static def processEventSheet(GoogleAPI googleAPI, sheetId){

    /**
     * First check if this is a new event. New events will not have an event id (uuid) in cell A2.
     */
    def eventIdCellAddress = "Event!A2"
    def response = googleAPI.sheets().spreadsheets().values().get(sheetId, eventIdCellAddress).execute();

    def eventId = response.getValues();
    if(eventId == null || eventId.isEmpty()){
        //This is a new event.
        
    }else{
        //This is an existing event.
    }
}