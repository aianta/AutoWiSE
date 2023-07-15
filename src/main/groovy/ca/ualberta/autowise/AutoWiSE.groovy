package ca.ualberta.autowise

import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.File
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.CompositeFuture
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject;
import org.slf4j.LoggerFactory;

import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.*
import static ca.ualberta.autowise.scripts.google.GetFilesInFolder.getFiles

/**
 * @Author Alexandru Ianta
 * This verticle is responsible for bootstrapping all of AutoWiSE's functionality.
 */

/**
 * GLOBAL VARIABLES
 */
//TICK = 600000 // 600,000 milliseconds, or 10 min ticks.
//TICK = 1000
log = LoggerFactory.getLogger(getClass())

void vertxStart(Promise<Void> promise){

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
        .setConfig(new JsonObject().put "path", "autowise-conf.yaml")
    ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(yamlOptionsStore))

    retriever.getConfig { configResult->

        if(!configResult){ // Handle failure to load config
            log.error "Failed to load configuration from autowise-conf.yaml!"
            throw new RuntimeException(configResult.cause().getMessage())
        }

        //Make config values available for initializing AutoWiSE systems
        def config = configResult.result()

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

        //Setup HTTP Server to handle webhooks
        vertx.executeBlocking(blocking->blocking.complete(AutoWiSEServer.createInstance(vertx, config.getString("host"), config.getInteger("port")))){
            res->
                if(res){
                    serverInit.complete(res.result())
                }else{
                    log.error res.cause().getMessage(), res.cause()
                    serverInit.fail(res.cause())
                }
        }

        //TODO: Establish connection to load active work from SQLite
        vertx.executeBlocking(blocking->blocking.complete(SQLite.createInstance(config.getString("db_connection_string")))){
            res ->
                if (res){
                    databaseInit.complete(res.result())
                }else{
                    log.error res.cause().getMessage(), res.cause()
                    databaseInit.fail(res.cause())
                }
        }

        CompositeFuture.all([
                googleAPIInit.future(),
                slackAPIInit.future(),
                databaseInit.future(),
                serverInit.future()
        ]).onComplete { setup->

            def googleApi = setup.result().resultAt(0)
            def slackApi = setup.result().resultAt(1)
            def db = setup.result().resultAt(2)
            def server = setup.result().resultAt(3)

            sendSlackMessage(slackApi, "#auto-wise", "Greetings from the script!")

            List<File> files = getFiles(googleApi, config.getString("autowise_drive_folder_id"), "application/vnd.google-apps.spreadsheet")
            files.forEach {f->log.info "${f.getName()} - ${f.getMimeType()} - ${f.getId()}"}

            /**
             * Once all setup is complete start the main loop of the system.
             * The logic below is executed every TICK.
             */
            periodId = vertx.setPeriodic(config.getLong("tick_rate"), id->{
                log.info "tick"

                /**
                 * On every tick check google drive for new events to process.
                 */


            })

            /** Notify vertx that verticle deployment is complete */
            promise.complete()
        }

    }



}


