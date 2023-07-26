package ca.ualberta.autowise.scripts.webhook

import groovy.transform.Field
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.hasVolunteerAlreadyCancelled
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.updateVolunteerContactStatusTable
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.updateVolunteerStatus
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.SendEmail.*
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.webhook.RejectVolunteeringForEvent.class)


static def rejectVolunteeringForEvent(services, webhook){

    def eventSheetId = webhook.data.getString("eventSheetId")
    def volunteerEmail = webhook.data.getString("volunteerEmail")

    if(hasVolunteerAlreadyCancelled(services.googleAPI, eventSheetId, volunteerEmail)){
        log.info "${volunteerEmail} has already cancelled or rejected for this event."
        return
    }

    def volunteerName = webhook.data.getString("volunteerName")
    def eventName = webhook.data.getString("eventName")
    def eventSlackChannel = webhook.data.getString("eventSlackChannel")


    //Update the status
    updateVolunteerStatus(services.googleAPI, eventSheetId, volunteerEmail, "Rejected", "-" )

    def emailTemplate = slurpDocument(services.googleAPI, webhook.data.getString("emailTemplateId"))
    def emailContents = emailTemplate.replaceAll("%EVENT_NAME%", eventName)

    sendEmail(services.googleAPI, "AutoWiSE", volunteerEmail, "[WiSER] Confirmation of Volunteer Opportunity Rejection for ${eventName}", emailContents )

    sendSlackMessage(services.slackAPI, eventSlackChannel, "${volunteerName} has indicated they are not interested or unable to volunteer for ${eventName}!")

}

