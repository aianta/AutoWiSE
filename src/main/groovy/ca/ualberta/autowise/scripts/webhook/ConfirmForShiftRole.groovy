package ca.ualberta.autowise.scripts.webhook

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.Webhook

import groovy.transform.Field
import io.vertx.core.Future
import org.slf4j.LoggerFactory



import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.hasVolunteerAlreadyCancelled


@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.webhook.ConfirmForShiftRole.class)
@Field static CONFIRMATION_TABLE = "\'Volunteer Confirmations\'!A1"


static def confirmShiftRole(services, Webhook webhook, Event event){

    def volunteerEmail = webhook.data.getString("volunteerEmail")

    return hasVolunteerAlreadyCancelled(services.db, event.id, volunteerEmail).compose{
        alreadyCancelled->
            if(alreadyCancelled){
                log.info "${volunteerEmail} has already cancelled or rejected for this event."
                return Future.failedFuture("You have already cancelled on one or rejected all volunteer shift-roles for this event!")
            }

            def volunteerName = webhook.data.getString("volunteerName")
            def shiftRoleString = webhook.data.getString("shiftRoleString")

            return services.db.confirmVolunteerShiftRole(event.sheetId, webhook.eventId, volunteerName, volunteerEmail, shiftRoleString )

    }

}