package ca.ualberta.autowise.model

import ca.ualberta.autowise.SlackAPI
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import io.vertx.core.json.JsonObject

import static ca.ualberta.autowise.scripts.slack.BlockingSlackMessage.sendSlackMessage

/**
 * Implements a 'browser' for handling the google auth flow via slack message.
 */
class SlackBrowser implements AuthorizationCodeInstalledApp.Browser{

    SlackAPI slackAPI
    JsonObject config

    @Override
    void browse(String url) throws IOException {
        sendSlackMessage(slackAPI, config.getString("technical_channel"), "Need to authorize GoogleAPI with Google account, go to the following link: ${url}")
    }
}
