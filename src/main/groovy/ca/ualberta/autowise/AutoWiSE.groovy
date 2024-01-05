package ca.ualberta.autowise

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.SlackBrowser
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.scripts.ManageEventStatusTable
import ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet
import ca.ualberta.autowise.scripts.ManageVolunteerConfirmationTable
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.drive.model.File
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

import org.slf4j.LoggerFactory

import java.awt.Composite
import java.time.ZoneId
import java.time.ZonedDateTime

import static ca.ualberta.autowise.scripts.ProcessAutoWiSEEventSheet.googleAPI
import static ca.ualberta.autowise.scripts.google.GetFilesInFolder.getFiles

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.*
import static ca.ualberta.autowise.scripts.tasks.RecruitmentEmailTask.recruitmentEmailTask
import static ca.ualberta.autowise.scripts.tasks.ConfirmationEmailTask.confirmationEmailTask
import static ca.ualberta.autowise.scripts.tasks.EventRegistrationEmailTask.eventRegistrationEmailTask
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.*
import static ca.ualberta.autowise.scripts.google.ErrorHandling.handleGoogleAPIError;

/**
 * @Author Alexandru Ianta
 * This verticle is responsible for bootstrapping all of AutoWiSE's functionality.
 */



@Field static JsonObject config
@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.AutoWiSE.class)

@Field static ZoneId timezone = ZoneId.of("Canada/Mountain"); //Set the zone id for all created timestamps

void vertxStart(Promise<Void> startup){



    /**
     * SETUP: Perform all start up logic here.
     * Define a set of promises for all async start up processes.
     */
    Promise<GoogleAPI> googleAPIInit = Promise.promise();
    Promise<SlackAPI> slackAPIInit = Promise.promise();
    Promise<SQLite> databaseInit = Promise.promise();
    Promise<AutoWiSEServer> serverInit = Promise.promise();

    //Load AutoWiSE yaml config - yaml is used so we can support comments

    ConfigStoreOptions yamlOptionsStore = new ConfigStoreOptions()
        .setType("file")
        .setFormat("yaml")
        .setConfig(new JsonObject().put( "path", "conf/autowise-conf.yaml"))
    ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(yamlOptionsStore))

    retriever.getConfig { configResult->

        if(!configResult){ // Handle failure to load config
            log.error "Failed to load configuration from autowise-conf.yaml!"
            throw new RuntimeException(configResult.cause().getMessage())
        }

        //Make config values available for initializing AutoWiSE systems
        //Lol jk, this doesn't work for some reason, proably async shenanigans
        config = configResult.result()


        //Initialize Authentication for Slack API
        vertx.executeBlocking(blocking->blocking.complete(SlackAPI.createInstance(config.getString("slack_token")))){
            res->
                if(res){
                    slackAPIInit.complete(res.result())

                }else{
                    log.error res.cause().getMessage(), res.cause()
                    slackAPIInit.fail(res.cause())
                }
        }

        //Init SQlite, then use that to init AutoWiSEServer
        vertx.executeBlocking(blocking->{
            Promise sqlitePromise = Promise.promise()
            SQLite.createInstance(vertx, config.getString("db_connection_string"), sqlitePromise.future())

            sqlitePromise.future().onSuccess{
                database->
                    databaseInit.complete(database) //Complete the database init future.
                    AutoWiSEServer server = AutoWiSEServer.createInstance(vertx, config, database)
                    serverInit.complete(server) //Complete the server init future.
            }.onFailure {
                err-> log.error err.getMessage(), err
            }
        })

        def slackAndDB = CompositeFuture.all(slackAPIInit.future(), databaseInit.future())
            .onSuccess {
                //Initialize Authentication for Google API, do this after slack and SQLite, so we can use slack to send the auth link and use the db to store info about api calls
                def googleAPI = GoogleAPI.createInstance(
                        new SlackBrowser(slackAPI: it.resultAt(0), config:config),
                        it.resultAt(1), //SQLite
                        vertx,
                        it.resultAt(0), //SlackAPI
                        config
                )
                googleAPIInit.complete(googleAPI)
            }

        CompositeFuture.all([
                googleAPIInit.future().onComplete{log.info "Google API initialized!"},
                slackAPIInit.future().onComplete{log.info "Slack API initialized!"},
                databaseInit.future().onComplete{log.info "Database initialized!"},
                serverInit.future().onComplete{log.info "AutoWise Server initialized!"}
        ]).onComplete { setup->
            log.info "about to make services map"

            GoogleAPI googleApi = setup.result().resultAt(0)
            SlackAPI slackApi = setup.result().resultAt(1)
            SQLite db = setup.result().resultAt(2)
            AutoWiSEServer server = setup.result().resultAt(3)
            log.info "about to make services map"
            //Package all the services into a map for easy passing to scripts
            def services = [
                    googleAPI: googleApi,
                    slackAPI: slackApi,
                    db: db,
                    server: server
            ]

            server.setServices(services)
            server.loadWebhooks()

            //Bolt app needs to run in a separate thread so as to not block AutoWiSE main loop.
            SlackBolt boltApp = new SlackBolt(services, config, this)
            Thread boltThread = new Thread(boltApp)
            boltThread.start()



            /**
             * Once all setup is complete start the main loops of the system.
             */
            googleSheetPeriodId = vertx.setPeriodic(config.getLong("external_tick_rate"), id->{
                if (config.getLong("external_tick_rate") > 3600000){ //If the tick rate is greater than 1h, send a heartbeat to slack.
                    sendSlackMessage(services.slackAPI, config.getString("technical_channel"), "Autowise Heartbeat - ${ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter)}")
                }

                boltApp.updateValidGDriveIds(); //refresh template options

//                doExternalTick(services, config)
//                    .onSuccess {
//                        log.info "External tick complete! {}", ZonedDateTime.now().format(EventSlurper.eventTimeFormatter)
//                    }
//                    .onFailure {
//                        log.error "External tick error! {}", ZonedDateTime.now().format(EventSlurper.eventTimeFormatter)
//                        log.error it.getMessage(), it
//                    }


            })

            internalPeriodId = vertx.setPeriodic(config.getLong("internal_tick_rate"), id->{
                log.info "internal tick - ${ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter)}"

                db.getWork().onSuccess(taskList->{
                    taskList.forEach( task-> {
                        task = (Task)task;
                        log.info "Retrieving associated event ({}) for task [{}] {}", task.eventId.toString(), task.taskId.toString(), task.name

                        db.getEvent(task.eventId)
                        .onFailure {log.error it.getMessage(), it}
                        .onSuccess {
                            executeTask(task, it, vertx, services, config).onSuccess{
                                log.info "Task ${task.taskId.toString()} executed sucessfully!"
                            }.onFailure{ err->
                                log.error "Error executing task ${task.taskId.toString()}!"
                                log.error err.getMessage(), err
                            }
                        }
                    })
                })

                }


            )

            try{

                log.info "about to refresh google api token"
                def refreshResult = googleApi.credentials.refreshToken()
                log.info "refresh result: ${refreshResult}"
            }catch(Exception e){
                log.error e.getMessage(),e
            }


            sendSlackMessage(services.slackAPI, config.getString("technical_channel"), "Autowise started successfully @${services.server.location()}!")
            /** Notify vertx that verticle deployment is complete */
            startup.complete()
        }

    }



}


static def queueEventSheetUpdate(Vertx vertx, services, Event event, config){
    //Queue up an event sheet update
    vertx.setTimer(config.getLong("sheet_update_delay", 15000), {
        updateEventSheet(services, event).onSuccess {
            log.info "Updated event sheet for {} - {} @ {}", event.id.toString(), event.name, ZonedDateTime.now().format(EventSlurper.eventTimeFormatter)
        }
    })

}


static def updateEventSheet(services, Event event){

    //Construct the sheet values
    return CompositeFuture.all(
            ManageEventStatusTable.generateEventStatusTable(services, event.sheetId),
            ManageEventVolunteerContactSheet.generateVolunteerContactStatusTable(services, event),
            ManageVolunteerConfirmationTable.generateVolunteerConfirmationTable(services, event.sheetId)
    ).compose {
        List<ValueRange> valueRanges = it.list();

        //Event status update
        ValueRange statusRange = new ValueRange()
        statusRange.setRange("Event!A3")
        statusRange.setValues([[event.status]])

        valueRanges.add(statusRange)

        return batchUpdate(services.googleAPI, event.sheetId, valueRanges)

    }

}

static def doExternalTick(services, config) {
    log.info "External tick!"
    SQLite db = services.db
    return db.getUpcomingEvents().compose {

        log.info "Upcoming events:"
        it.forEach { event ->
            log.info "{} - {}", event.id.toString(), event.name
            if (event.status == EventStatus.IN_PROGRESS.toString() ) {

                //Other parts of the event sheet won't have updates until after recruitment emails are sent.
                db.isInitialRecruitmentComplete(event.id).compose { initialRecruitmentTaskComplete->
                    if(initialRecruitmentTaskComplete){
                        log.info "Updating spreadsheet {} for event {} - {}", event.sheetId, event.id.toString(), event.name

                        List<Future> todoForEvent = [
                                ManageEventStatusTable.updateEventStatusTable(services, event.sheetId),
                                ManageEventVolunteerContactSheet.updateVolunteerContactStatusTable(services, event),
                                ManageVolunteerConfirmationTable.updateVolunteerConfirmationTable(services, event.sheetId)
                        ]

                        CompositeFuture.all(todoForEvent).onSuccess {
                            log.info "Spreadsheet {} for event {} - {} updated successfully!", event.sheetId, event.id.toString(), event.name
                        }
                    }
                }

                //Mark events whose end time has elapsed as complete.
                if (event.endTime.isBefore(ZonedDateTime.now())) {
                    db.updateEventStatus(event.id, EventStatus.COMPLETE)
                }


            }

        }

        return Future.succeededFuture()
    }
}

//    def server = services.server
//    def googleApi = services.googleAPI
//
//    log.info "external tick - ${ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter)}"
//
//    try{
//        log.info "Refreshing webhooks"
//        server.loadWebhooks() //(re)Load webhooks from database.
//
//
//        /**
//         * On every tick check google drive for new events to process.
//         */
//        /**
//         * Start going through all the google sheets in the autowise folder on google drive.
//         */
//        getFiles(googleApi, config.getString("autowise_drive_folder_id"), "application/vnd.google-apps.spreadsheet")
//                .onSuccess {
//                    files->
//                        files.forEach {f->
//                            log.info "${f.getName()} - ${f.getMimeType()} - ${f.getId()}"
//                            /**
//                             * If the sheet name starts with the specified autowise_event_prefix process it
//                             */
//                            if (f.getName().startsWith(config.getString("autowise_event_prefix"))){
//
//                                // Do processing in separate thread to avoid blocking the main loop.
//                                vertx.executeBlocking(blocking->{
//
//                                    processEventSheet(services, f.getId(), config)
//                                            .onSuccess {
//                                                blocking.complete()
//                                            }.onFailure{err->
//                                        log.error err.getMessage(), err
//                                    }
//                                }, true){
//                                    log.info "Allegedly done processing"
//                                }
//
//
//                            }
//                        }
//                }
//                .onFailure { err->
//                    log.error "Error getting files from google drive!"
//                    log.error err.getMessage(), err
//                }
//
//
//    }catch(Exception e){
//        log.error e.getMessage(), e
//
//    }



static def executeTask(Task task, Event event, vertx, services, config){
    return vertx.executeBlocking(blocking->{
        _executeTask(task, event, vertx, services, config)
            .onComplete{
                blocking.complete()
            }
    },true)
}

static def _executeTask(Task task, Event event, Vertx vertx, services, config){


    switch (task.name) {
        case "AutoWiSE Event Registration Email":
            return eventRegistrationEmailTask(services, task, event, config.getString("autowise_new_recruitment_campaign_email_template"), config)
                .compose {return queueEventSheetUpdate(vertx, services, event, config)}
                .onSuccess{
                    log.info "Event registration email task complete."
                }.onFailure{err-> handleTaskError(config, services, err, task)}
            break
        case "Initial Recruitment Email":
            return recruitmentEmailTask(vertx, services, task, event, config, (status)->{
                return status.equals("Not Contacted")
            }, "[WiSER] Volunteer opportunities for ${event.name}!")
                    .compose {return queueEventSheetUpdate(vertx, services, event, config)}
                    .onSuccess{
                        log.info "Initial Recruitment Email task executed successfully!"
                    }.onFailure{err-> handleTaskError(config, services, err, task)}
            break
        case "Recruitment Email":
            return recruitmentEmailTask(vertx, services, task, event, config, (status)->{
                return status.equals("Not Contacted") || status.equals("Waiting for response")
            }, "[WiSER] Volunteer opportunities for ${event.name}!")
                    .compose {return queueEventSheetUpdate(vertx, services, event, config)}
                    .onSuccess{
                        log.info "Recruitment email task executed successfully!"
                    }.onFailure{err-> handleTaskError(config, services, err, task)}

            break
        case "Follow-up Email":
            return confirmationEmailTask(vertx, services, task, event, config, "[WiSER] Confirm your upcomming volunteer shift for ${event.name}!" )
                    .compose {return queueEventSheetUpdate(vertx, services, event, config)}
                    .onSuccess{
                        log.info "Confirmation email task completed successfully!"
                    }.onFailure{err-> handleTaskError(config, services, err, task)}
            break
        default: return Future.failedFuture("Unrecognized task name: ${task.name}")

    }

    return Future.failedFuture("Error executing task!")
}

static void handleTaskError(config, services, Throwable err, Task task){
    log.error "Error executing task ${task.name} [${task.taskId.toString()}]", err

    if (err instanceof GoogleJsonResponseException){
        //Handle google API exception.
        handleGoogleAPIError(config, services, (GoogleJsonResponseException)err, task.eventId, task.name, "task")
    }

}

