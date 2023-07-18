package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.EventStatus
import groovy.transform.Field
import io.vertx.core.Future
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.google.EventSlurper.slurpSheet
import static ca.ualberta.autowise.scripts.google.EventSlurper.isSlurpable
import static ca.ualberta.autowise.scripts.RegisterNewEvent.registerNewEvent
import static ca.ualberta.autowise.scripts.SendEmailWithTemplate.sendEmailWithTemplate

@Field static def log = LoggerFactory.getLogger(ProcessAutoWiSEEventSheet.class)
@Field static GoogleAPI googleAPI

static def processEventSheet(services, sheetId, volunteerSheetId, volunteerTableRange){

    googleAPI = services.googleAPI

    /**
     * First check if this is a new event. New events will not have an event id (uuid) in cell A2.
     *
     * Pull event status to start. Only process if event status is READY.
     */
    def eventStatusCellAddress = "Event!A3"

    def response = googleAPI.sheets().spreadsheets().values().get(sheetId, eventStatusCellAddress).execute()
    def status = response.getValues()

    if(status == null || status.isEmpty() || !isSlurpable(status.get(0).get(0))){
        log.info "Event sheet ${sheetId} status is null, empty, or not-slurpable skipping..."
        return
    }

    def event = slurpSheet(googleAPI, sheetId)

    switch(status.get(0).get(0)){

        case EventStatus.READY.toString():
            assert event.id == null //Event Id should be null at this stage
            return registerNewEvent(services, event, sheetId, volunteerSheetId, volunteerTableRange)
        default:
            log.error "Unrecognized event status!"
            return Future.succeededFuture()

    }

}