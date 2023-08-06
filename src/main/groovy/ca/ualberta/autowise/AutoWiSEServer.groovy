package ca.ualberta.autowise

import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.TaskStatus
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.net.JksOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.LoggerHandler
import org.slf4j.LoggerFactory
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

        try{
            HttpServerOptions options = new HttpServerOptions()
                    .setHost(HOST)
                    .setPort(PORT)
                    .setUseAlpn(true)
                    .setSsl(true)
                    .setKeyStoreOptions( new JksOptions()
                            .setPath(config.getString("jks_path"))
                            .setPassword(config.getString("jks_password")))

            log.info "AutoWiSE Server using ssl!"



            server = vertx.createHttpServer(options)

            router = Router.router(vertx)
            server.requestHandler(router).listen(PORT,  listenResult->{
                if(listenResult){
                    log.info "AutoWiSE Server running at ${config.getString("protocol")}://${HOST}:${PORT}"
                }else{
                    log.error listenResult.cause().getMessage(), listenResult.cause()
                    throw listenResult.cause()
                }
            })
            router.route().handler(LoggerHandler.create());
        }catch(Exception err){
            log.error err.getMessage(), e
        }




    }

    def location(){
        return "${config.getString("protocol")}://${HOST}:${PORT}"
    }

    def loadWebhooks(){
        db.getActiveWebhooks().onSuccess(hooks->{
            log.info "Mounting ${hooks.size()} active webhooks"

            router.clear() //Clear any existing webhooks
            router.route().handler(LoggerHandler.create());
            hooks.forEach(hook->this.mountWebhook(hook))
            router.route().last().handler(rc->{
                rc.response().setStatusCode(200).end("This link may have expired or is invalid.")
            })
        }).onFailure(err->{
            log.error err.getMessage(), err
        })
    }

    def mountWebhook(Webhook hook){
        try{
            def webhookPath = Base64.encodeBase64URLSafeString(hook.id.toString().getBytes())
            log.info "Mounting webhook ${hook.id.toString()} at ${webhookPath}"
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
                    finishResponse(rc, "Please note: If you already signed up for a volunteer shift, clicking a different volunteer link from the recruitment email will NOT do anything.")
                    acceptShiftRole(services, webhook, config)
                            .onSuccess{
                                log.info "ACCEPT_ROLE_SHIFT webhook ${webhook.id.toString()} executed successfully!"
                            }
                            .onFailure{err->webhookFailureHandler(err, webhook)}
                    break
                case HookType.CANCEL_ROLE_SHIFT:
                    finishResponse(rc, "We are cancelling your shift and will send you a confirmation soon.")
                    cancelShiftRole(services, webhook, config)
                        .onSuccess{
                            log.info "CANCEL_ROLE_SHIFT webhook ${webhook.id.toString()} executed successfully!"
                        }
                        .onFailure{ err->webhookFailureHandler(err, webhook)}
                    break
                case HookType.CONFIRM_ROLE_SHIFT:
                    finishResponse(rc,"Your availability for your volunteer shift has been confirmed.")
                    confirmShiftRole(services, webhook)
                        .onSuccess{log.info("CONFIRM_ROLE_SHIFT webhook ${webhook.id.toString()} executed successfully!")}
                        .onFailure(err->webhookFailureHandler(err, webhook))
                    break
                case HookType.REJECT_VOLUNTEERING_FOR_EVENT:
                    finishResponse(rc,"Sorry it didn't work out this time.")
                    rejectVolunteeringForEvent(services, webhook)
                        .onSuccess{log.info("REJECT_VOLUNTEERING_FOR_EVENT webhook ${webhook.id.toString()} executed successfully!")}
                        .onFailure{err->webhookFailureHandler(err, webhook)}
                    break

                case HookType.EXECUTE_TASK_NOW:
                    finishResponse(rc, "The specified task will begin execution imminently")
                    db.getWorkByTaskId(webhook.data.getString("taskId"))
                        .onSuccess{
                            task->AutoWiSE.executeTask(task, vertx, services, config)
                                    .onSuccess{
                                        log.info "Successfully executed task ${webhook.data.getString("taskId")} through webhook  ${webhook.id.toString()}!"
                                    }
                                    .onFailure{
                                        log.error "Error executing task  ${webhook.data.getString("taskId")} through webhook ${webhook.id.toString()}!"
                                    }
                        }.onFailure{
                        err->
                            log.error "Error attempting to fetch task to execute now!"
                            log.error err.getMessage(), err
                    }

                    break
                case HookType.CANCEL_TASK:
                    finishResponse(rc, "The specified task will be cancelled imminently. If you'd like to cancel the entire campaign click the campaign cancellation link instead.")
                    db.cancelTaskById(webhook.data.getString("taskId"))
                        .onSuccess{
                            log.info "Successfully cancelled task ${webhook.data.getString("taskId")}!"
                        }
                        .onFailure{
                            err->log.error err.getMessage(), err
                        }
                    break
                case HookType.CANCEL_CAMPAIGN:
                    finishResponse(rc, "The campaign will be cancelled imminently.")
                    db.cancelCampaign(webhook.eventId).onSuccess{
                        log.info "Campaign for eventId ${webhook.eventId.toString()} has been cancelled."
                        updateSingleValueAt(services.googleAPI, webhook.data.getString("eventSheetId"), 'Event!A3', TaskStatus.CANCELLED.toString())

                    }.onFailure{ err->
                        log.error "Error cancelling campaign for event id: ${webhook.eventId.toString()}"
                        log.error err.getMessage(), err
                    }
                    break
                case HookType.BEGIN_CAMPAIGN:
                    finishResponse(rc, "The campaign tasks will be scheduled according to plan imminently.")
                    db.beginCampaign(webhook.eventId).onSuccess{
                        log.info "Campagin for eventId ${webhook.eventId.toString()} has begun!"
                        updateSingleValueAt(services.googleAPI, webhook.data.getString("eventSheetId"), "Event!A3", TaskStatus.IN_PROGRESS.toString())
                    }.onFailure{
                        err-> log.error "Error starting campaign for event id: ${webhook.eventId.toString()}"
                            log.error err.getMessage(), err
                    }
                    break
                default:log.warn "Unknown hook type! ${webhook.type.toString()}"
            }


        }}.onFailure {
            err->
                if (err.getMessage().equals("Already invoked!")){
                    rc.response().setStatusCode(400).end("This link has already been used! You may now close this window.")
                }else{
                    rc.response().setStatusCode(500).end("Whoops! You should let the WiSER team know you saw this @${ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter)}")
                }
        }

    }

    static def finishResponse(rc, customMsg){
        rc.response().setStatusCode(200).end("Your request has been processed! Thank you!\n\n${customMsg}\n\nYou may now close this window.")
    }


    static def webhookFailureHandler(Throwable err, webhook){
        log.error "Error executing webhook ${webhook.id.toString()}!"
        log.error err.getMessage(), err
    }
}
