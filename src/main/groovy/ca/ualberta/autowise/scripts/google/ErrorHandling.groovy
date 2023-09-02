package ca.ualberta.autowise.scripts.google

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import groovy.transform.Field
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.sendSlackMessage

@Field static def log = LoggerFactory.getLogger(ErrorHandling.class)

public static void handleGoogleAPIError(config, services, GoogleJsonResponseException err, eventId, name, sourceType){
    GoogleJsonError googleError = ((GoogleJsonResponseException) err).getDetails();
    switch(googleError.getCode()){
        case 404:
            /**
             *  The event sheet, or an email template doc was not found. In either case
             *  something is terribly amiss if we get to this point so we should cancel the
             *  campaign and notify the autowise technical channel of what happened.
             */
            log.error "Got 404 when attempting to access Google API resource. Cancelling campaign and notifying technical slack channel."

            services.db.cancelCampaign(eventId).onSuccess{
                sendSlackMessage(services.slackAPI, config.getString("technical_channel"), "Campaign for event [${eventId.toString()}] cancelled due to missing resource. Got 404 from Google API during ${name} ${sourceType} execution." )
            }.onFailure{ cancelErr->
                def errorString = "Error handling 404 exception for event ${eventId.toString()}, could not cancel campaign."
                log.error errorString
                sendSlackMessage(services.slackAPI, config.getString("technical_channel"), errorString)
            }
            break;
        default:
            log.error "Unhandled google API error code: ${googleError.getCode()}"
    }

}