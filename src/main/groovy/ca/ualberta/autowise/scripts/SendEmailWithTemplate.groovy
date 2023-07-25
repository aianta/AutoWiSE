package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.MassPersonalizedEmailTask
import groovy.transform.Field
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory

import java.util.stream.Collectors

import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.SendEmail.sendEmail

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.SendEmailWithTemplate.class)

static def messingAround(services, Vertx vertx, config){
    MassPersonalizedEmailTask emailTask = null;


    CompositeFuture.all(
            emailTask.contentResolution(), //Fetch dynamic content for these emails
            emailTask.targets() //Fetch the targets of the emails (list of email addresses to send to).
    ).compose {compositeFuture->

        def content = compositeFuture.resultAt(0)
        Set<String> targets = compositeFuture.resultAt(1)

        //Create a chain of future compositions which sequentially send emails to all targets.
        Iterator<String> it = targets.iterator();
        Future lastFuture = null
        int counter = 0
        while (it.hasNext()){
            counter++
            String target = it.next()
            String emailBody = emailTask.assembleContent(target, content)
            def f = send(services.googleAPI, vertx, config, emailBody, target)
            f.onSuccess {
                log.info "Sent email ${counter}/${targets.size()}"
                emailTask.confirmSentSuccessfully(target)
            }

            if (lastFuture != null){
                lastFuture.compose(done->f)
            }

            last = f
        }

        lastFuture.onSuccess {
            log.info "Sent all emails."
        }.onFailure{err->
            log.error "Error while sending emails!"
            log.error err.getMessage(), err
        }

        return emailTask.postTaskBookkeeping()
    }


}



static def sendEmailWithTemplate(GoogleAPI googleAPI, templateId, targetEmail){

    def templateText = slurpDocument(googleAPI, templateId)

    log.info "Email template:\n${templateText}"

    sendEmail(googleAPI, 'AutoWiSE', 'aianta03@gmail.com', '[AutoWiSE] Test Message', templateText)


}

