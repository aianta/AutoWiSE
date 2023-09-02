package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.EventStatus
import groovy.transform.Field
import io.vertx.core.Future
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.google.EventSlurper.slurpSheet
import static ca.ualberta.autowise.scripts.google.EventSlurper.isSlurpable
import static ca.ualberta.autowise.scripts.RegisterNewEvent.registerNewEvent
import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValueAt
import static ca.ualberta.autowise.scripts.google.CreateEventSheet.createEventSheet

@Field static def log = LoggerFactory.getLogger(ProcessAutoWiSEEventSheet.class)
@Field static GoogleAPI googleAPI

static def processEventSheet(services, sheetId, config){

    googleAPI = services.googleAPI

    /**
     * First check if this is a new event. New events will not have an event id (uuid) in cell A2.
     *
     * Pull event status to start. Only process if event status is READY.
     */
    def eventStatusCellAddress = "Event!A3"
    log.info "Checking status"
    return getValueAt(googleAPI, sheetId, eventStatusCellAddress).compose{
        status->
            log.info "Event sheet ${sheetId} status: ${status}"
            if(status == null || status.isEmpty() || !isSlurpable(status)){
                log.info "Event sheet ${sheetId} status is null, empty, or not-slurpable skipping..."
                return Future.succeededFuture()
            }

            return slurpSheet(googleAPI, sheetId).compose{
                event->
                switch(status){
                    case EventStatus.READY.toString():
                        assert event.id == null //Event Id should be null at this stage
                        return registerNewEvent(services, event, sheetId, config)
                    default:
                        log.warn "Unrecognized event status!"
                        return Future.succeededFuture()

                }
            }
    }

}