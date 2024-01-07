package ca.ualberta.autowise.model

import ca.ualberta.autowise.GoogleAPI
import groovy.transform.Field
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory

import java.time.Instant
import java.util.function.BiFunction
import java.util.function.Consumer

import static ca.ualberta.autowise.scripts.google.SendEmail.sendEmail

class MassEmailEntry{
    String subject
    String content
    String target
}

/**
 * @Author Alexandru Ianta
 *
 * This class helps facilitate the sending of personalized emails at scale. IE: When we need to send 100 emails, one by one, with unique links in each one.
 *
 * The idea is that those types of emails are sent by processing the 'volunteer contact status' table row by row.
 * So the MassEmail sender accepts such a table an iterable, then sets up a while loop with future management such
 * that each email is:
 *  1. Executed with vertx.executeBlocking in its own thread, freeing the main loop.
 *  2. Executed with 'mass_email_delay' number of milliseconds delay to respect the 2 emails/sec max for the gmail API.
 *  3. Executed in a chain of sequential futures, such that the next email sends only after the first completes.
 *
 *  This is achieved by passing the caller the contactStatus for each row in the iterable that was used to initialize MassEmailSender
 *  as well as a future that will complete when the email for that particular row will have been sent.
 *
 *  In return MassEmailSender expects a MassEmailEntry object containing all information required to send an email corresponding
 *  with the row it passed to the caller via the BiFunction.
 *
 *  If the caller gives back a null object, MassEmailSender assumes no email is to be sent for that row and proceed to the next iteration
 *  of the while loop.
 *
 *  Finally sendMassEmail also provides a future that will complete when all emails for the iterable have been sent.
 */
class MassEmailSender {

    static log = LoggerFactory.getLogger(MassEmailSender.class)

    def it
    Vertx vertx
    def services
    def config
    long emailCounter = 0L

    public MassEmailSender(Vertx vertx, services, config, Iterable it){
        this.it = it.iterator()
        this.vertx = vertx
        this.services = services
        this.config = config
    }

    public sendMassEmail(BiFunction<ContactStatus, Future, MassEmailEntry> rowFunction, Consumer<Future> complete){
        this.emailCounter = 0L;
        Future lastFuture = null
        while (it.hasNext()){
            ContactStatus contactStatus = it.next()

            def future  = vertx.executeBlocking(blocking->{
                Promise emailEntryPromise = Promise.promise() //Completes when this specific email has been sent.
                MassEmailEntry emailEntry = rowFunction.apply(contactStatus, emailEntryPromise.future())
                if(emailEntry == null){
                    log.info "Email entry was null!"
                    emailEntryPromise.complete() // Complete the email entry promise
                    blocking.complete() //If we're given a null email entry, this row must not require an email so we move on to the next.
                    return
                }else{
                    log.info "Got an email entry to send!"
                    //Otherwise there is an email to send so let's do that.
                    this.emailCounter++;

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
                }


            })

            if(lastFuture != null){
                lastFuture.compose { return future }
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
     * TODO: if we have issues with sending larger volumes of emails, have to figure out how to stagger these.
     * @param googleAPI
     * @param vertx
     * @param config
     * @param subject
     * @param emailBody
     * @param target
     * @return
     */
    private Future send(GoogleAPI googleAPI,  config, subject, emailBody, target){

       return sendEmail(config, googleAPI, config.getString("sender_email"), target, subject, emailBody)
    }

}
