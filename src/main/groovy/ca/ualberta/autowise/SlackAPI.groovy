package ca.ualberta.autowise

import com.slack.api.Slack
import com.slack.api.methods.MethodsClient

/**
 * @author Alexandru Ianta
 * Gives scripts access to the Slack API.
 */
class SlackAPI {

    static instance;

    static createInstance(token){
        instance = new SlackAPI(token)
        return instance
    }

    static getInstance(){
        if(instance == null){
            throw RuntimeException("Cannot get SlackAPI instance as it was not created")
        }
        return instance
    }

    def authToken;
    MethodsClient methods;
    Slack slack = Slack.getInstance();

    private SlackAPI(token){
        authToken = token
        methods = slack.methods(authToken)
    }

    def methods(){
        return methods;
    }

}
