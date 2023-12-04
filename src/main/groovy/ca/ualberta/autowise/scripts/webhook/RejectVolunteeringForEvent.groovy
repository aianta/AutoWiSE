package ca.ualberta.autowise.scripts.webhook

import ca.ualberta.autowise.model.Webhook
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.hasVolunteerAlreadyCancelled

import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.updateVolunteerStatus
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.SendEmail.*
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.webhook.RejectVolunteeringForEvent.class)


static def rejectVolunteeringForEvent(services, Webhook webhook, config){

    def eventSheetId = webhook.data.getString("eventSheetId")
    def volunteerEmail = webhook.data.getString("volunteerEmail")

    return hasVolunteerAlreadyCancelled(services.db, webhook.eventId, volunteerEmail)
        .compose{
            alreadyCancelled->
                if (alreadyCancelled){
                    log.info "${volunteerEmail} has already cancelled or rejected for this event."
                    return Future.failedFuture("You have already cancelled one or rejected all volunteer opportunities for this event!")
                }

                def volunteerName = webhook.data.getString("volunteerName")
                def eventName = webhook.data.getString("eventName")
                def eventSlackChannel = webhook.data.getString("eventSlackChannel")

                return CompositeFuture.all(
                        updateVolunteerStatus(services.db, webhook.eventId, eventSheetId, volunteerEmail, "Rejected", "-" ),      //Update the status
                        slurpDocument(services.googleAPI, webhook.data.getString("emailTemplateId"))
                ).compose{
                    composite->
                        def emailTemplate = composite.resultAt(1)
                        def emailContents = makeRejectedEmail(emailTemplate, eventName)

                        return CompositeFuture.all(
                                sendEmail(config, services.googleAPI, config.getString("sender_email"), volunteerEmail, "[WiSER] Confirmation of Volunteer Opportunity Rejection for ${eventName}", emailContents ),
                                sendSlackMessage(services.slackAPI, eventSlackChannel, "${volunteerName} has indicated they are not interested or unable to volunteer for ${eventName}!")
                        )
                }
        }
}

static def makeRejectedEmail(template, eventName){
   return template.replaceAll("%EVENT_NAME%", eventName)
}

