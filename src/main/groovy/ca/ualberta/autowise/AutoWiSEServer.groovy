package ca.ualberta.autowise

import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.TaskStatus
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import io.vertx.core.http.HttpMethod;
import org.apache.commons.codec.binary.Base64

import java.time.ZonedDateTime

import static ca.ualberta.autowise.scripts.webhook.SignupForRoleShift.*
import static ca.ualberta.autowise.scripts.webhook.CancelShiftRole.*
import static ca.ualberta.autowise.scripts.webhook.ConfirmForShiftRole.*
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateSingleValueAt
import static ca.ualberta.autowise.scripts.webhook.RejectVolunteeringForEvent.*

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
    SQLite db;
    def config
    def services
    static instance;

    static createInstance(vertx, config, db){
        HOST = config.getString("host")
        PORT = config.getInteger("port")
        instance = new AutoWiSEServer(vertx, db, config);
        return instance
    }

    static getInstance(){
        if(instance == null){
            throw new RuntimeException("Cannot retrieve AutoWiSEServer instance as it has not been created")
        }
        return instance;
    }

    def setServices(services){
        this.services = services
        log.info "Services initialized!"
    }

    private AutoWiSEServer(vertx, SQLite db, config){
        this.config = config
        this.vertx = vertx
        this.db = db

        HttpServerOptions options = new HttpServerOptions()
                .setHost(HOST)
                .setPort(PORT)


        server = vertx.createHttpServer(options)

        router = Router.router(vertx)
        server.requestHandler(router).listen(PORT)

        log.info "AutoWiSE Server running at ${HOST}:${PORT}"
    }

    def loadWebhooks(){
        db.getActiveWebhooks().onSuccess(hooks->{
            router.clear() //Clear any existing webhooks
            hooks.forEach(hook->this.mountWebhook(hook))
        }).onFailure(err->{
            log.error err.getMessage(), err
        })
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

        db.invokeAndGetWebhookById(webhookId).onSuccess{ Webhook webhook->{

            log.info( "hook type match [${webhook.type.toString()}:${HookType.ACCEPT_ROLE_SHIFT.toString()}] ${webhook.type.equals(HookType.ACCEPT_ROLE_SHIFT)}")

            switch (webhook.type){
                case HookType.ACCEPT_ROLE_SHIFT:
                    acceptShiftRole(services, webhook, config)
                    break
                case HookType.CANCEL_ROLE_SHIFT:
                    cancelShiftRole(services, webhook, config)
                    break
                case HookType.CONFIRM_ROLE_SHIFT:
                    confirmShiftRole(services, webhook)
                    break
                case HookType.REJECT_VOLUNTEERING_FOR_EVENT:
                    rejectVolunteeringForEvent(services, webhook);
                    break

                case HookType.EXECUTE_TASK_NOW:

                    db.getWorkByTaskId(webhook.data.getString("taskId"))
                        .onSuccess{
                            task->AutoWiSE.executeTask(task, vertx, services, config)
                        }.onFailure{
                        err->
                            log.error "Error attempting to fetch task to execute now!"
                            log.error err.getMessage(), err
                    }

                    break
                case HookType.CANCEL_TASK:
                    db.cancelTaskById(webhook.data.getString("taskId"))
                        .onFailure{
                            err->log.error err.getMessage(), err
                        }
                    break
                case HookType.CANCEL_CAMPAIGN:
                    db.cancelCampaign(webhook.eventId).onSuccess{
                        log.info "Campaign for eventId ${webhook.eventId.toString()} has been cancelled."
                        updateSingleValueAt(services.googleAPI, webhook.data.getString("eventSheetId"), 'Event!A3', TaskStatus.CANCELLED.toString())

                    }.onFailure{ err->
                        log.error "Error cancelling campaign for event id: ${webhook.eventId.toString()}"
                        log.error err.getMessage(), err
                    }
                    break


                default:log.warn "Unknown hook type! ${webhook.type.toString()}"
            }

            rc.response().setStatusCode(200).end("Your request has been processed! Thank you! You may now close this window.")

        }}.onFailure {
            err->
                if (err.getMessage().equals("Already invoked!")){
                    rc.response().setStatusCode(400).end("This link has already been used! You may now close this window.")
                }else{
                    rc.response().setStatusCode(500).end("Whoops! You should let the WiSER team know you saw this @${ZonedDateTime.now().format(EventSlurper.eventTimeFormatter)}")
                }
        }

    }
}
