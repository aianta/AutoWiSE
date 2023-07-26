package ca.ualberta.autowise.scripts.webhook

import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import org.slf4j.LoggerFactory

import java.time.ZonedDateTime

import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.hasVolunteerAlreadyCancelled
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.*

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.webhook.ConfirmForShiftRole.class)
@Field static CONFIRMATION_TABLE = "\'Volunteer Confirmations\'!A1"


static def confirmShiftRole(services, Webhook webhook){

    def volunteerEmail = webhook.data.getString("volunteerEmail")
    def eventSheetId = webhook.data.getString("eventSheetId")

    if(hasVolunteerAlreadyCancelled(services.googleAPI, eventSheetId, volunteerEmail)){
        log.info "${volunteerEmail} has already cancelled or rejected for this event."
        return
    }

    def volunteerName = webhook.data.getString("volunteerName")
    def shiftRoleString = webhook.data.getString("shiftRoleString")

    ValueRange valueRange = new ValueRange()
    valueRange.setValues([[volunteerEmail, volunteerName, shiftRoleString, ZonedDateTime.now().format(EventSlurper.eventTimeFormatter)]])
    valueRange.setMajorDimension("ROWS")
    valueRange.setRange(CONFIRMATION_TABLE)

    appendAt(services.googleAPI, eventSheetId, CONFIRMATION_TABLE, valueRange)



}