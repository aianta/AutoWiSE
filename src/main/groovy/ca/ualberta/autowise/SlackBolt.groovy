package ca.ualberta.autowise

import com.slack.api.bolt.App
import com.slack.api.bolt.jetty.SlackAppServer
import io.vertx.core.json.JsonObject

class SlackBolt {

    App app;
    SlackAppServer server;
    JsonObject config;
    def services

    public SlackBolt(services, config){
        this.config = config
        this.services = services
        this.app = new App()

        this.app.command("/do_external_tick", (req, ctx) -> {
            AutoWiSE.doExternalTick(services)
            return ctx.ack()
        })

        this.server = new SlackAppServer(app)
        server.start()
    }
}
