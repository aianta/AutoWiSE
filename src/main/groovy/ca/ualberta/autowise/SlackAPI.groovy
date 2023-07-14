package ca.ualberta.autowise

import com.slack.api.Slack
import com.slack.api.methods.MethodsClient

/**
 * @author Alexandru Ianta
 * Gives scripts access to the Slack API.
 */
class SlackAPI {

    static instance;

    static createInstance(){
        instance = new SlackAPI()
        return instance
    }

    static getInstance(){
        if(instance == null){
            createInstance()
        }
        return instance
    }

    def authToken;
    MethodsClient methods;
    Slack slack = Slack.getInstance();

    private SlackAPI(){
        authToken = System.getenv("SLACK_TOKEN")
        methods = slack.methods(authToken)
    }

    def methods(){
        return methods;
    }

}
