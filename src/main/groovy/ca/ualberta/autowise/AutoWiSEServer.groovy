package ca.ualberta.autowise

import ca.ualberta.autowise.model.Webhook
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import io.vertx.core.http.HttpMethod;
import org.apache.commons.codec.binary.Base64

/**
 * @Author Alexandru Ianta
 *
 * Implements a webserver to provide webhooks for various triggers.
 */
class AutoWiSEServer {

    static final log = LoggerFactory.getLogger(AutoWiSEServer.class)

    //Some default values, they are overwritten in createInstance()
    static PORT = 8080;
    static HOST = '0.0.0.0'

    Vertx vertx;
    HttpServer server;
    Router router;

    static instance;

    static createInstance(vertx, host, port){
        HOST = host
        PORT = port
        instance = new AutoWiSEServer(vertx);
        return instance
    }

    static getInstance(){
        if(instance == null){
            throw new RuntimeException("Cannot retrieve AutoWiSEServer instance as it has not been created")
        }
        return instance;
    }

    private AutoWiSEServer(vertx){
        this.vertx = vertx

        HttpServerOptions options = new HttpServerOptions()
                .setHost(HOST)
                .setPort(PORT)


        server = vertx.createHttpServer(options)

        router = Router.router(vertx)
        server.requestHandler(router).listen(PORT)

        log.info "AutoWiSE Server running at ${HOST}:${PORT}"
    }

    def mountWebhook(Webhook hook){
        try{
            log.info "Mounting webhook!"
            def webhookPath = Base64.encodeBase64URLSafeString(hook.id.toString().getBytes())
            log.info "mounting webhook to path: ${webhookPath}"
            router.route(HttpMethod.GET, "/"+webhookPath).handler(this::webhookHandler)

        }catch (Exception e){
            log.error e.getMessage() , e
        }
    }

    def webhookHandler(RoutingContext rc){
        def encodedHookId = rc.request().path().substring(1) //Cut off the starting slash
        log.info "encodedHookId: ${encodedHookId}"
        def webhookId = UUID.fromString(new String(Base64.decodeBase64(encodedHookId)))
        log.info "decoded webhook id: ${webhookId.toString()}"
    }
}
