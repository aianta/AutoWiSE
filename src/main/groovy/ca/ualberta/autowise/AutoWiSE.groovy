package ca.ualberta.autowise

import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.File
import io.vertx.core.CompositeFuture
import io.vertx.core.Promise;
import io.vertx.core.Future;

/**
 * @Author Alexandru Ianta
 * This verticle is responsible for bootstrapping all of AutoWiSE's functionality.
 */

/**
 * GLOBAL VARIABLES
 */
//TICK = 600000 // 600,000 milliseconds, or 10 min ticks.
TICK = 1000

void vertxStart(Promise<Void> promise){

    /**
     * SETUP: Perform all start up logic here.
     * Define a set of promises for all async start up processes.
     */
    Promise googleAPIInit = Promise.promise();
    Promise slackAPIInit = Promise.promise();

    //TODO: Initialize Authentication for Google API
    vertx.executeBlocking(blocking->blocking.complete(GoogleAPI.createInstance())){
        res->
            if(res){
                googleApi = res.result()
                googleAPIInit.complete()
            }else{
                googleAPIInit.fail(res.cause())
            }
    }



    //TODO: Initialize Authentication for Slack API

    //TODO: Setup HTTP Server to handle webhooks

    //TODO: Load active work from SQLite

    CompositeFuture.all(
            [googleAPIInit.future()]
    ).onComplete {

        FileList fileList = googleApi.drive().files().list().setPageSize(10).execute()
        List<File> files = fileList.getFiles();
        if (files == null || files.isEmpty()){
            println "No files found."
        }else{
            println "Files:"
            files.forEach {file->
                println "${file.getName()} ${file.getId()}"
            }
        }

        /**
         * Once all setup is complete start the main loop of the system.
         * The logic below is executed every TICK.
         */
        periodId = vertx.setPeriodic(TICK, id->{
            println "tick"

            /**
             * On every tick check google drive for new events to process.
             */


        })

        /** Notify vertx that verticle deployment is complete */
        promise.complete()
    }

}


