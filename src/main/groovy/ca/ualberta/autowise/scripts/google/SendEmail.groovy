package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import groovy.transform.Field
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.apache.commons.codec.binary.Base64
import com.google.api.services.gmail.model.Message as GMessage
import org.slf4j.LoggerFactory

/**
 * @author Alexandru Ianta
 *  Adapted from <a href="https://developers.google.com/gmail/api/guides/sending#sending_messages">Gmail API docs example</a>
 *  Be mindful of <a href="https://developers.google.com/gmail/api/reference/quota">API Quota</a>
 *
 *  Note: The google API docs say to use javax.mail.* . Some digging revealed that project to have
 *  been moved. It is now called jakarta mail.
 *
 *  See October 23, 2020 entry: <a href="https://jakartaee.github.io/mail-api/#API_Documentation">Jakarta EE Mail Docs</a>
 *
 *  See https://support.google.com/a/answer/166852 for other quotas/sending limit considerations.
 *
 */

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.SendEmail.class)

static def sendEmailToGroup(googleAPI, from, to, subject, content ){
    MimeMessage email = createMimeMessage(from, subject, content)
    to.forEach(
            recipient->{
                email.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient))
            }
    )
    _sendEmail(googleAPI, email)
}

//TODO -> insert 1 second delay before sending
static def sendEmail(googleAPI, from, to, subject, content){
    MimeMessage email = createMimeMessage(from, subject, content)
    email.addRecipient(Message.RecipientType.TO, new InternetAddress(to))
    _sendEmail(googleAPI, email)
}

static def sendEmail(googleAPI, from, to, bcc, subject, content){
    MimeMessage email = createMimeMessage(from, subject, content)
    email.addRecipient(Message.RecipientType.TO, new InternetAddress(to))
    email.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc))
    _sendEmail(googleAPI, email)
}

private static def _sendEmail(GoogleAPI googleAPI, MimeMessage email){
    log.info "Sending email"
    try{
        ByteArrayOutputStream buffer = new ByteArrayOutputStream()
        email.writeTo(buffer)
        byte[] rawMessageBytes = buffer.toByteArray()
        String encodedEmail = Base64.encodeBase64URLSafeString(rawMessageBytes)
        GMessage message = new GMessage()
        message.setRaw(encodedEmail)
        googleAPI.gmail().users().messages().send("me", message).execute()
        log.info "Message Id: ${message.getId()}"
        log.info "${message.toPrettyString()}"

    }catch (GoogleJsonResponseException | Exception e ) {
        // TODO(developer) - handle error appropriately
        GoogleJsonError error = e.getDetails();
        log.error e.getMessage(), e
        throw e
    }

}

private static def createMimeMessage(from, subject, text){
    try{
        log.info "Creating MimeMessage"
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props)
        MimeMessage email =  new MimeMessage(session)
        email.setFrom(from)
        email.setSubject(subject.toString()) //toString any GStrings that made it here
        email.setText(text.toString(), "utf-8", "html") //toString any GStrings that made it here
        return email
    }catch(Exception e){
        log.error e.getMessage(), e
    }
   return null
}

