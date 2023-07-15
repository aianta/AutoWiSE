package ca.ualberta.autowise

import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.File
import io.vertx.core.CompositeFuture
import io.vertx.core.Promise;
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
TICK = 1000
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

    //Initialize Authentication for Google API
    vertx.executeBlocking(blocking->blocking.complete(GoogleAPI.createInstance())){
        res->
            if(res){
                googleAPIInit.complete(res.result())
            }else{
                googleAPIInit.fail(res.cause())
            }
    }

    //Initialize Authentication for Slack API
    vertx.executeBlocking(blocking->blocking.complete(SlackAPI.createInstance())){
        res->
            if(res){
                slackAPIInit.complete(res.result())
            }else{
                log.error res.cause().getMessage(), res.cause()
                slackAPIInit.fail(res.cause())
            }
    }

    //TODO: Setup HTTP Server to handle webhooks
    vertx.executeBlocking(blocking->blocking.complete(AutoWiSEServer.createInstance(vertx))){
        res->
            if(res){
                serverInit.complete(res.result())
            }else{
                log.error res.cause().getMessage(), res.cause()
                serverInit.fail(res.cause())
            }
    }

    //TODO: Establish connection to load active work from SQLite
    vertx.executeBlocking(blocking->blocking.complete(SQLite.createInstance())){
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

        //sendSlackMessage(slackApi, "#auto-wise", "Greetings from the script!")

        List<File> files = getFiles(googleApi, "14kqs83NGNJYOtzhx6iG2sJoR9MTi6f4N", "application/vnd.google-apps.spreadsheet")
        files.forEach {f->log.info "${f.getName()} - ${f.getMimeType()} - ${f.getId()}"}

        /**
         * Once all setup is complete start the main loop of the system.
         * The logic below is executed every TICK.
         */
        periodId = vertx.setPeriodic(TICK, id->{
            log.info "tick"

            /**
             * On every tick check google drive for new events to process.
             */


        })

        /** Notify vertx that verticle deployment is complete */
        promise.complete()
    }

}


