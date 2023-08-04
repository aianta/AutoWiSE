package ca.ualberta.autowise

import ca.ualberta.autowise.model.SlackBrowser
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.drive.model.File
import groovy.transform.Field
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject

import org.slf4j.LoggerFactory

import java.time.ZoneId
import java.time.ZonedDateTime

import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.findAvailableShiftRoles
import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.syncEventVolunteerContactSheet
import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.GetFilesInFolder.getFiles
import static ca.ualberta.autowise.scripts.ProcessAutoWiSEEventSheet.processEventSheet
import static ca.ualberta.autowise.scripts.tasks.RecruitmentEmailTask.recruitmentEmailTask
import static ca.ualberta.autowise.scripts.tasks.ConfirmationEmailTask.confirmationEmailTask
import static ca.ualberta.autowise.scripts.tasks.EventRegistrationEmailTask.eventRegistrationEmailTask
import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.*
/**
 * @Author Alexandru Ianta
 * This verticle is responsible for bootstrapping all of AutoWiSE's functionality.
 */



@Field static JsonObject config
@Field static log = LoggerFactory.getLogger(getClass())

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

        CompositeFuture.all([
                googleAPIInit.future().onComplete{log.info "Google API initialized!"},
                slackAPIInit.future().onSuccess{slackAPI->
                    //Initialize Authentication for Google API, do this after slack, so we can use slack as the browser
                    def googleAPI = GoogleAPI.createInstance(
                            config.getString("application_name"),
                            config.getString("credentials_path"),
                            config.getString("auth_tokens_directory_path"),
                            config.getString("auth_server_host"),
                            config.getInteger("auth_server_receiver_port"),
                            new SlackBrowser(slackAPI: slackAPI, config:config)
                    )
                    googleAPIInit.complete(googleAPI)
                }.onComplete{log.info "Slack API initialized!"},
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

            Set<Volunteer> volunteers = [] as Set
            volunteers.add(new Volunteer(email: "ianta@ualberta.ca", name: "Alex Ianta"))
            volunteers.add(new Volunteer(email: "aianta03@gmail.com", name:"Tim tom"))




            /**
             * Once all setup is complete start the main loops of the system.
             */
            googleSheetPeriodId = vertx.setPeriodic(config.getLong("external_tick_rate"), id->{
                log.info "external tick - ${ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter)}"

                try{
                    log.info "Refreshing webhooks"
                    server.loadWebhooks() //(re)Load webhooks from database.


                    /**
                     * On every tick check google drive for new events to process.
                     */
                    /**
                     * Start going through all the google sheets in the autowise folder on google drive.
                     */
                    getFiles(googleApi, config.getString("autowise_drive_folder_id"), "application/vnd.google-apps.spreadsheet")
                        .onSuccess {
                            files->
                                files.forEach {f->
                                    log.info "${f.getName()} - ${f.getMimeType()} - ${f.getId()}"
                                    /**
                                     * If the sheet name starts with the specified autowise_event_prefix process it
                                     */
                                    if (f.getName().startsWith(config.getString("autowise_event_prefix"))){

                                        // Do processing in separate thread to avoid blocking the main loop.
                                        vertx.executeBlocking(blocking->{

                                            processEventSheet(services, f.getId(), config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range"))
                                                    .onSuccess {
                                                blocking.complete()
                                            }.onFailure{err->
                                                log.error err.getMessage(), err
                                            }
                                        }, true){
                                            log.info "Allegedly done processing"
                                        }


                                    }
                                }
                        }
                        .onFailure { err->
                            log.error "Error getting files from google drive!"
                            log.error err.getMessage(), err
                        }


                }catch(Exception e){
                    log.error e.getMessage(), e

                }


            })

            internalPeriodId = vertx.setPeriodic(config.getLong("internal_tick_rate"), id->{
                log.info "internal tick - ${ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter)}"

                db.getWork().onSuccess(taskList->{
                    taskList.forEach( task-> {
                        log.info "Looking through task ${task.name}"

                        executeTask(task, vertx, services, config).onSuccess{
                            log.info "Task ${task.taskId.toString()} executed sucessfully!"
                        }.onFailure{ err->
                            log.error "Error executing task ${task.taskId.toString()}!"
                            log.error err.getMessage(), err
                        }

                    })
                })

                }


            )

            sendSlackMessage(services.slackAPI, config.getString("technical_channel"), "Autowise started successfully!")
            /** Notify vertx that verticle deployment is complete */
            startup.complete()
        }

    }



}


static def executeTask(task, vertx, services, config){
    return vertx.executeBlocking(blocking->{
        _executeTask(task, vertx, services, config)
            .onComplete{
                blocking.complete()
            }
    },true)
}

static def _executeTask(task, vertx, services, config){

    switch (task.name) {
        case "AutoWiSE Event Registration Email":
            return eventRegistrationEmailTask(services, task, config.getString("autowise_new_recruitment_campaign_email_template"), config)
                .onSuccess{
                    log.info "Event registration email task complete."
                }
                .onFailure{
                    log.error "Error while executing event registration email task."
                }


            break
        case "Initial Recruitment Email":
            return recruitmentEmailTask(vertx, services, task, config, (status)->{
                return status.equals("Not Contacted")
            }, "[WiSER] Volunteer opportunities for ${task.data.getString("eventName")}!")
            .onSuccess{
                log.info "Initial Recruitment Email task executed successfully!"
            }
            .onFailure{ err->
                log.error "Error during initial recruitment email task!"
                log.error err.getMessage(), err
            }
            break
        case "Recruitment Email":
            return recruitmentEmailTask(vertx, services, task, config, (status)->{
                return status.equals("Not Contacted") || status.equals("Waiting for response")
            }, "[WiSER] Volunteer opportunities for ${task.data.getString("eventName")}!")
            .onSuccess{
                log.info "Recruitment email task executed successfully!"
            }
            .onFailure{ err->
                log.error "Error during recruitment email task!"
                log.error err.getMessage(), err
            }

            break
        case "Follow-up Email":
            return confirmationEmailTask(vertx, services, task, config, "[WiSER] Confirm your upcomming volunteer shift for ${task.data.getString("eventName")}!" )
                .onSuccess{
                    log.info "Confirmation email task completed successfully!"
                }
                .onFailure{err->
                    log.error "Error during confirmation email task!"
                    log.error err.getMessage(), err
                }
            break
        default: return Future.failedFuture("Unrecognized task name: ${task.name}")

    }
}