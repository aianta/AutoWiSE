package ca.ualberta.autowise

import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.File
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import io.vertx.core.CompositeFuture
import io.vertx.core.Promise;
import org.slf4j.LoggerFactory;

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

    //TODO: Initialize Authentication for Google API
    vertx.executeBlocking(blocking->blocking.complete(GoogleAPI.createInstance())){
        res->
            if(res){
                googleApi = res.result()
                googleAPIInit.complete(googleApi)
            }else{
                googleAPIInit.fail(res.cause())
            }
    }

    vertx.executeBlocking(blocking->blocking.complete(SlackAPI.createInstance())){
        res->
            if(res){
                slackApi = res.result()
                slackAPIInit.complete(slackApi)
            }else{
                log.info "Woah"
                log.error res.cause().getMessage(), res.cause()
                slackAPIInit.fail(res.cause())
            }
    }



    //TODO: Initialize Authentication for Slack API

    //TODO: Setup HTTP Server to handle webhooks

    //TODO: Load active work from SQLite

    CompositeFuture.all([
            googleAPIInit.future(),
            slackAPIInit.future()
    ]).onComplete { setup->

        def googleApi = setup.result().resultAt(0)
        def slackApi = setup.result().resultAt(1)

        log.info "Sending a slack message"
        try{
            ChatPostMessageRequest chatPostMessageRequest = ChatPostMessageRequest.builder()
                    .channel("#auto-wise")
                    .text(":exploding_head: bro...")
                    .build();

            ChatPostMessageResponse response = slackApi.methods().chatPostMessage(chatPostMessageRequest)
            log.error response.error
            log.info "Slack message OK? ${response.ok}"

            if (response.errors != null && response.errors.size() > 0){
                response.errors.forEach {err-> log.error err}
            }

        }catch (Exception e){

            log.error e.getMessage(), e
        }


        log.info "hitting the google drive api"

        FileList fileList = googleApi.drive().files().list().setPageSize(10).execute()
        List<File> files = fileList.getFiles();
        if (files == null || files.isEmpty()){
            log.info "No files found."
        }else{
            log.info "Files:"
            files.forEach {file->
                log.info "${file.getName()} ${file.getId()}"
            }
        }

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


