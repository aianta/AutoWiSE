package ca.ualberta.autowise

import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.handler.builtin.GlobalShortcutHandler
import com.slack.api.bolt.jetty.SlackAppServer
import com.slack.api.bolt.response.Response
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.socket_mode.SocketModeClient
import groovy.transform.Field
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import com.slack.api.model.view.View;

import static ca.ualberta.autowise.scripts.slack.BlockingSlackMessage.*



class SlackBolt implements Runnable{

    static def log = LoggerFactory.getLogger(ca.ualberta.autowise.SlackBolt.class)
    App app;
    SlackAppServer server;
    SocketModeApp socketModeApp;
    JsonObject config;
    def services
    def autoWiSE

    public SlackBolt( services,  config,  autoWiSE){
        this.config = config
        this.services = services
        this.autoWiSE = autoWiSE

        AppConfig boltConfig = new AppConfig()
        boltConfig.setSigningSecret(config.getString("slack_signing_secret"))
        boltConfig.setSingleTeamBotToken(config.getString("slack_token"))

        this.app = new App(boltConfig)

        this.app.viewSubmission("create_new_campaign", (req, ctx)->{

            def stateValues = req.getPayload().getView().getState().getValues()
            String eventName = stateValues.get("event_name_block").get("event_name").getValue()

            log.info "Event name was: ${eventName}"

            return ctx.ack()
        })

        this.app.command("/new_vol_recruit_campaign", (req, ctx) -> {
            log.info "neat!"

            def response = ctx.client().viewsOpen{
                it.triggerId(req.getPayload().getTriggerId())
                        .viewAsString("""
{
	"type": "modal",
	"callback_id": "create_new_campaign",
	"title": {
		"type": "plain_text",
		"text": "My App",
		"emoji": true
	},
	"submit": {
		"type": "plain_text",
		"text": "Submit",
		"emoji": true
	},
	"close": {
		"type": "plain_text",
		"text": "Cancel",
		"emoji": true
	},
	"blocks": [
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Let's create a new event"
			}
		},
		{
			"type": "divider"
		},
		{
			"type": "input",
			"block_id": "event_name_block",
			"element": {
				"type": "plain_text_input",
				"action_id": "event_name"
			},
			"label": {
				"type": "plain_text",
				"text": "Event Name",
				"emoji": false
			}
		},
		{
			"type": "divider"
		},
		{
			"type": "actions",
			"elements": [
				{
					"type": "button",
					"text": {
						"type": "plain_text",
						"text": "Create ",
						"emoji": true
					},
					"value": "create_event",
					"action_id": "0"
				}
			]
		}
	]
}    """)
            }

            if(response.isOk()){
                return ctx.ack()
            }else{
                return Response.builder().statusCode(500).body(response.getError()).build()
            }

        })



        socketModeApp = new SocketModeApp(
                config.getString("socket_mode_token"),
                SocketModeClient.Backend.JavaWebSocket,
                app
        );


        sendSlackMessage(services.slackAPI, config.getString("technical_channel"), "Bolt App online!")
    }

    /**
     * Notifies the slack channel that the bolt app is online and ready to receive commands.
     */
    def notifyOnline(){

    }

    @Override
    void run() {
        socketModeApp.start()
        log.info "I still have access to my variables, look: ${config.encodePrettily()}"
    }
}
