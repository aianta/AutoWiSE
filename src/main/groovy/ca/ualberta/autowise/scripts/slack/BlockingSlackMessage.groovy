package ca.ualberta.autowise.scripts.slack

import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import groovy.transform.Field

import org.slf4j.LoggerFactory


@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.slack.SendSlackMessage.class)

static def sendSlackMessage(slackApi, channel, message){


    log.info "Sending a slack message"

    try{
        ChatPostMessageRequest chatPostMessageRequest = ChatPostMessageRequest.builder()
                .channel(channel)
                .text(message)
                .build();

        ChatPostMessageResponse response = slackApi.methods().chatPostMessage(chatPostMessageRequest)

        log.info "Slack message OK? ${response.ok}"
        if (response.error != null){
            log.error response.error
            throw new IOException(response.error)
        }
        if (response.errors != null && response.errors.size() > 0){
            response.errors.forEach {err-> log.error err}
            throw new IOException(response.errors.get(0))
        }

    }catch (Exception e){

        log.error e.getMessage(), e
    }

}

