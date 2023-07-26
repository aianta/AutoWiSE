package ca.ualberta.autowise.model

import ca.ualberta.autowise.GoogleAPI
import groovy.transform.Field
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory

import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

import static ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.updateVolunteerStatus
import static ca.ualberta.autowise.scripts.google.SendEmail.sendEmail

class MassEmailEntry{
    String subject
    String content
    String target
}

class MassEmailSender {

    static log = LoggerFactory.getLogger(MassEmailSender.class)

    def it
    Vertx vertx
    def services
    def config

    public MassEmailSender(Vertx vertx, services, config, Iterable it){
        this.it = it.iterator()
        this.vertx = vertx
        this.services = services
        this.config = config
    }

    public sendMassEmail(BiFunction<List<Object>, Future, MassEmailEntry> rowFunction, Consumer<Future> complete){
        Future lastFuture = null
        while (it.hasNext()){
            def rowData = it.next()

            def future  = vertx.executeBlocking(blocking->{
                Promise emailEntryPromise = Promise.promise() //Completes when this specific email has been sent.
                MassEmailEntry emailEntry = rowFunction.apply(rowData, emailEntryPromise.future())
                if(emailEntry == null){
                    emailEntryPromise.complete()
                    blocking.complete() //If we're given a null email entry, this row must not require an email so we move on to the next.
                }
                send(services.googleAPI, config, emailEntry.subject, emailEntry.content, emailEntry.target)
                    .onSuccess{
                        log.info "Email (${emailEntry.subject}) sent to  ${emailEntry.target}"
                        emailEntryPromise.complete()
                        blocking.complete()
                    }
                    .onFailure{
                        err->
                            log.error "Error sending email (${emailEntry.subject}) to ${emailEntry.target}"
                            log.error err.getMessage(), err
                            blocking.fail(err)
                            emailEntryPromise.fail(err)
                    }
            })

            if(lastFuture != null){
                lastFuture.compose(done->future)
            }

            lastFuture = future
        }

        lastFuture.onSuccess{
            complete.accept(Future.succeededFuture())
        }.onFailure{err->
            complete.accept(Future.failedFuture(err))
        }
    }

    /**
     * NOTE: Be sure this executes inside vertx.blocking...
     * @param googleAPI
     * @param vertx
     * @param config
     * @param subject
     * @param emailBody
     * @param target
     * @return
     */
    private Future send(GoogleAPI googleAPI,  config, subject, emailBody, target){
        Promise promise = Promise.promise()

        Thread.sleep(config.getLong("mass_email_delay"))
        sendEmail(googleAPI, "AutoWiSE", target, subject, emailBody)
        promise.complete()

        return promise.future();
    }

}
