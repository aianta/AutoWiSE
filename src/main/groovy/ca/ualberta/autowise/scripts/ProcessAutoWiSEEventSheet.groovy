package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.EventStatus
import groovy.transform.Field
import io.vertx.core.Future
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.google.EventSlurper.slurpSheet
import static ca.ualberta.autowise.scripts.RegisterNewEvent.registerNewEvent
import static ca.ualberta.autowise.scripts.SendEmailWithTemplate.sendEmailWithTemplate

@Field static def log = LoggerFactory.getLogger(ProcessAutoWiSEEventSheet.class)
@Field static GoogleAPI googleAPI

static def processEventSheet(services, sheetId){

    googleAPI = services.googleAPI

    /**
     * First check if this is a new event. New events will not have an event id (uuid) in cell A2.
     *
     * Pull eventId and event status to start. Only process if event status is READY.
     */
    //TODO: Refactor this to only retrieve the status
    def eventIdCellAddress = "Event!A2"
    def eventStatusCellAddress = "Event!A3"
    def ranges = [eventIdCellAddress, eventStatusCellAddress]

    def response = googleAPI.sheets().spreadsheets().values().batchGet(sheetId).setRanges(ranges).execute()
    def idAndStatus = response.getValueRanges()

    def status = idAndStatus.get(1)
    if(status == null || status.isEmpty() || !status.getValues().get(0).get(0).equals(EventStatus.READY.toString())){
        log.info "Event sheet ${sheetId} is null, empty or not marked 'READY' skipping..."
        return
    }

    def event = slurpSheet(googleAPI, sheetId)
    log.info "event.id: ${event.id} - ${event.id == null}"

    //TODO: for testing only, remove
    sendEmailWithTemplate(services.googleAPI, event.recruitmentEmailTemplateId, "lol@gmail.com")

    if(event.id == null){
        //This is a new event.
        return registerNewEvent(services, event, sheetId)
    }else{
        //This is an existing event.
        return Future.succeededFuture()
    }
}