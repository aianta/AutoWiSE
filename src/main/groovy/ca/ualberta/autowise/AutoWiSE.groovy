package ca.ualberta.autowise


import com.google.api.services.drive.model.File
import groovy.transform.Field
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.rxjava3.ext.auth.sqlclient.SqlAuthentication;
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.google.GetFilesInFolder.getFiles
import static ca.ualberta.autowise.scripts.ProcessAutoWiSEEventSheet.processEventSheet
import static ca.ualberta.autowise.scripts.EventRegistrationEmailTask.eventRegistrationEmailTask
import static ca.ualberta.autowise.scripts.InitialRecruitmentEmailTask.initialRecruitmentEmailTask

/**
 * @Author Alexandru Ianta
 * This verticle is responsible for bootstrapping all of AutoWiSE's functionality.
 */

log = LoggerFactory.getLogger(getClass())

@Field static JsonObject config

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
        .setConfig(new JsonObject().put( "path", "autowise-conf.yaml"))
    ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(yamlOptionsStore))

    retriever.getConfig { configResult->

        if(!configResult){ // Handle failure to load config
            log.error "Failed to load configuration from autowise-conf.yaml!"
            throw new RuntimeException(configResult.cause().getMessage())
        }

        //Make config values available for initializing AutoWiSE systems
        config = configResult.result()

        //Initialize Authentication for Google API
        vertx.executeBlocking(blocking->blocking.complete(GoogleAPI.createInstance(
                config.getString("application_name"),
                config.getString("credentials_path"),
                config.getString("auth_tokens_directory_path"),
                config.getInteger("auth_server_receiver_port")
        ))){
            res->
                if(res){
                    googleAPIInit.complete(res.result())
                }else{
                    log.error res.cause().getMessage(), res.cause()
                    googleAPIInit.fail(res.cause())
                }
        }

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
                googleAPIInit.future(),
                slackAPIInit.future(),
                databaseInit.future(),
                serverInit.future()
        ]).onComplete { setup->

            GoogleAPI googleApi = setup.result().resultAt(0)
            SlackAPI slackApi = setup.result().resultAt(1)
            SQLite db = setup.result().resultAt(2)
            AutoWiSEServer server = setup.result().resultAt(3)

            //Package all the services into a map for easy passing to scripts
            def services = [
                    googleAPI: googleApi,
                    slackAPI: slackApi,
                    db: db,
                    server: server
            ]

            server.setServices(services)

            server.loadWebhooks() //Load webhooks from database.

            //sendSlackMessage(slackApi, "#auto-wise", "Greetings from the script!")



            /**
             * Start going through all the google sheets in the autowise folder on google drive.
             */
            List<File> files = getFiles(googleApi, config.getString("autowise_drive_folder_id"), "application/vnd.google-apps.spreadsheet")

            files.forEach {f->
                log.info "${f.getName()} - ${f.getMimeType()} - ${f.getId()}"
                /**
                 * If the sheet name starts with the specified autowise_event_prefix process it
                 */
                if (f.getName().startsWith(config.getString("autowise_event_prefix"))){

                    // Do processing in separate thread to avoid blocking the main loop.
                    vertx.executeBlocking(blocking->{

                        processEventSheet(services, f.getId(), config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range")).onSuccess {
                            blocking.complete()
                        }
                    }){
                        log.info "Allegedly done processing"
                    }


                }
            }

            /**
             * Once all setup is complete start the main loops of the system.
             */
            googleSheetPeriodId = vertx.setPeriodic(config.getLong("external_tick_rate"), id->{
                log.info "external tick"

                /**
                 * On every tick check google drive for new events to process.
                 */

            })

            internalPeriodId = vertx.setPeriodic(config.getLong("internal_tick_rate"), id->{
                log.info "internal tick"

                db.getWork().onSuccess(taskList->{
                    taskList.forEach( task-> {
                        log.info "Looking through task ${task.name}"

                        switch (task.name) {
                            case "AutoWiSE Event Registration Email":

                                //eventRegistrationEmailTask(services, task, config.getString("autowise_new_recruitment_campaign_email_template"))

                                break
                            case "Initial Recruitment Email":

                                initialRecruitmentEmailTask(services, task, config.getString("autowise_volunteer_pool_id"), config.getString("autowise_volunteer_table_range"))

                                break
                            case "Recruitment Email":
                                break
                            case "Follow-up Email":
                                break

                        }
                    })
                })

                }


            )

            /** Notify vertx that verticle deployment is complete */
            startup.complete()
        }

    }



}


