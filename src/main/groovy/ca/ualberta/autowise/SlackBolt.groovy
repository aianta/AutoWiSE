package ca.ualberta.autowise

import ca.ualberta.autowise.scripts.slack.NewCampaignBlock
import ca.ualberta.autowise.scripts.slack.RolesBlock
import ca.ualberta.autowise.scripts.slack.ShiftsBlock
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
                        .viewAsString(NewCampaignBlock.viewString())
            }

            if(response.isOk()){
                return ctx.ack()
            }else{
                return Response.builder().statusCode(500).body(response.getError()).build()
            }

        })

        this.app.command("/roles_test", (req, ctx)->{

            def response = ctx.client().viewsOpen{
                it.triggerId(req.getPayload().getTriggerId())
                .view(RolesBlock.makeView(3))
            }

            if(response.isOk()){
                return ctx.ack()
            }else{
                return Response.builder().statusCode(500).body(response.getError()).build()
            }
        })

        this.app.command("/shifts_test", (req,ctx)->{
            def response = ctx.client().viewsOpen{
                it.triggerId(req.getPayload().getTriggerId())
                .view(ShiftsBlock.makeView("Grill Master", 3))
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
