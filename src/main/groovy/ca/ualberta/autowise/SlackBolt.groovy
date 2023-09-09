package ca.ualberta.autowise

import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.jetty.SlackAppServer
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.socket_mode.SocketModeClient
import groovy.transform.Field
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import ca.ualberta.autowise.AutoWiSE

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

        this.app.command("/do_external_tick", (req, ctx) -> {
            log.info "neat!"
            autoWiSE.doExternalTick(services, config)
            return ctx.ack()
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
