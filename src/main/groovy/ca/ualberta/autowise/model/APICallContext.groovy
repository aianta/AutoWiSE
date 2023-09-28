package ca.ualberta.autowise.model

import ca.ualberta.autowise.AutoWiSE
import io.vertx.core.json.JsonObject

import java.time.LocalDateTime

class APICallContext extends JsonObject{

    UUID id
    LocalDateTime timestamp = LocalDateTime.now(AutoWiSE.timezone);

    public APICallContext(){
        method(StackWalker.getInstance()
        .walk(stream -> stream.skip(1).findFirst().get() )
        .getMethodName())
    }

    void attempt(int attempt){
        put "attempt", attempt
    }

    int attempt(){
        getInteger("attempt")
    }

    void docId(String docId){
        put "docId", docId
    }

    def docId(){
        return getString("docId")
    }

    void sheetId(String sheetId){
        put("sheetId", sheetId)
    }

    String sheetId(){
        return getString("sheetId")
    }

    void method(String method){
        put("method", method)
    }

    String method(){
        return getString("method")
    }


}
