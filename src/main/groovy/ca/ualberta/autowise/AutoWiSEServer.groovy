package ca.ualberta.autowise

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import org.slf4j.LoggerFactory

/**
 * @Author Alexandru Ianta
 *
 * Implements a webserver to provide webhooks for various triggers.
 */
class AutoWiSEServer {

    static final log = LoggerFactory.getLogger(AutoWiSEServer.class)

    def PORT = 8080;
    def HOST = '0.0.0.0'

    Vertx vertx;
    HttpServer server;
    Router router;

    static instance;

    static createInstance(vertx){
        instance = new AutoWiSEServer(vertx);
        return instance
    }

    static getInstance(){
        if(instance == null){
            log.error "Cannot retrieve instance as it has not been created"
            return null
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
}
