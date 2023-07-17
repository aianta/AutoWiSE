package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import groovy.transform.Field
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.google.DocumentSlurper.slurpDocument
import static ca.ualberta.autowise.scripts.google.SendEmail.sendEmail

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.SendEmailWithTemplate.class)

static def sendEmailWithTemplate(GoogleAPI googleAPI, templateId, targetEmail){

    def templateText = slurpDocument(googleAPI, templateId)

    log.info "Email template:\n${templateText}"

    sendEmail(googleAPI, 'AutoWiSE', 'aianta03@gmail.com', '[AutoWiSE] Test Message', templateText)


}

