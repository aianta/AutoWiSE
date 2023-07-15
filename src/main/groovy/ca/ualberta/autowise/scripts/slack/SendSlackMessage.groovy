package ca.ualberta.autowise.scripts.slack

import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import org.slf4j.LoggerFactory


static def sendSlackMessage(slackApi, channel, message){
    def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.slack.SendSlackMessage.class)

    log.info "Sending a slack message"

    try{
        ChatPostMessageRequest chatPostMessageRequest = ChatPostMessageRequest.builder()
                .channel(channel)
                .text(message)
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



}

