package ca.ualberta.autowise.scripts.webhook

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.ManageEventStatusTable
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.hasVolunteerAlreadyCancelled

import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.updateVolunteerStatus
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.SendEmail.*
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.webhook.RejectVolunteeringForEvent.class)


static def rejectVolunteeringForEvent(services, Webhook webhook, Event event, config){

    def volunteerEmail = webhook.data.getString("volunteerEmail")

    return hasVolunteerAlreadyCancelled(services.db, webhook.eventId, volunteerEmail)
        .compose{
            alreadyCancelled->
                if (alreadyCancelled){
                    log.info "${volunteerEmail} has already cancelled or rejected for this event."
                    return Future.failedFuture("You have already cancelled one or rejected all volunteer opportunities for this event!")
                }

                def volunteerName = webhook.data.getString("volunteerName")

                return CompositeFuture.all(
                        updateVolunteerStatus(services.db, webhook.eventId, event.sheetId, volunteerEmail, "Rejected", "-" ),      //Update the status
                        slurpDocument(services.googleAPI, event.confirmRejectedEmailTemplateId)
                ).compose{
                    composite->
                        def emailTemplate = composite.resultAt(1)
                        def emailContents = makeRejectedEmail(emailTemplate, event.name)

                        return CompositeFuture.all(
                                sendEmail(config, services.googleAPI, config.getString("sender_email"), volunteerEmail, "[WiSER] Confirmation of Volunteer Opportunity Rejection for ${event.name}", emailContents ),
                                sendSlackMessage(services.slackAPI, event.eventSlackChannel, "${volunteerName} has indicated they are not interested or unable to volunteer for ${event.name}!")
                        )
                }
        }
}

static def makeRejectedEmail(template, eventName){
   return template.replaceAll("%EVENT_NAME%", eventName)
}

