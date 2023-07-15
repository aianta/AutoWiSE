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
}
