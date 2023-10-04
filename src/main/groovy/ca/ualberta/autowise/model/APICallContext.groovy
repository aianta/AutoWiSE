package ca.ualberta.autowise.model

import ca.ualberta.autowise.AutoWiSE
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class APICallContext extends JsonObject{

    public static final DateTimeFormatter timestampFormat = DateTimeFormatter.ofPattern("LLLL dd, yyyy @ hh:mm:ss.SSS a")
    private static final Logger log = LoggerFactory.getLogger(APICallContext.class)

    UUID id = UUID.randomUUID()
    LocalDateTime timestamp = LocalDateTime.now(AutoWiSE.timezone);
    Throwable err = null;

    public APICallContext(){

        //TODO: test these filtering assumptions!
        def history = StackWalker.getInstance()
                .walk(stream -> stream.filter(frame->
                        !frame.getMethodName().equals("<init>") && //Ignore this constructor
                                !frame.getMethodName().equals("fromCache") && //Ignore some groovy stuff
                                !frame.getMethodName().equals("doCall") &&
                                frame.getFileName() != null &&
                                !frame.getFileName().contains(".java")) //All of our code comes from .groovy files so if it's coming from a .java file we're probably not interested.
                        .map(frame->"${frame.getFileName()}:${frame.getLineNumber()}#${frame.getMethodName()}".toString())
                        .collect(StringBuilder::new, (sb,s)->sb.append(s + "\n"), StringBuilder::append) ).toString()

        method(
            history

        )
        attempt(1)
    }

    /**
     * Turns GString values into strings before calling the {@link JsonObject#put} method.
     * @param key   the key
     * @param value the value
     * @return
     */
    APICallContext put (String key, Object value){
        if (value instanceof GString){
            return super.put(key, value.toString())
        }else{
            return super.put(key,value)
        }
    }

    APICallContext duration(long duration){
        put "duration_raw", duration
        put "duration", "${duration}ms"
    }

    String error(){
        return getString("error")
    }

    APICallContext error(Throwable err){
        this.err = err
        put "error", err.getMessage()

        //Capture the stack trace of the error
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)
        err.printStackTrace(pw)
        put "stacktrace", pw.toString()

        put "errorType", err.getClass().getName()

        //Strip additional info from GoogleAPI exceptions
        if(err instanceof GoogleJsonResponseException){
            GoogleJsonError innerError = ((GoogleJsonResponseException)err).getDetails()
            put "errorCode", innerError.getCode()

            innerError.getDetails()

            put "apiErrorDetails", innerError.getDetails().stream()
                .map {def errDetail = new JsonObject()
                        errDetail.put "detail", it.getDetail()
                        errDetail.put "reason", it.getReason()
                        errDetail.put "type", it.getType()
                        errDetail.put "parameterViolations", it.getParameterViolations().stream()
                            .map { def paramViolation = new JsonObject()
                                paramViolation.put "description", it.description
                                paramViolation.put "parameter", it.parameter
                                return paramViolation
                            }
                            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll)
                    return errDetail
                }
                .collect(JsonArray::new, JsonArray::add, JsonArray::addAll)
        }

        return this
    }

    APICallContext attempt(int attempt){
        put "attempt", attempt
        return this
    }

    int attempt(){
        getInteger("attempt")
    }

    APICallContext docId(String docId){
        put "docId", docId
        return this
    }

    def docId(){
        return getString("docId")
    }

    APICallContext sheetId(String sheetId){
        put("sheetId", sheetId)
        return this
    }

    String sheetId(){
        return getString("sheetId")
    }

    APICallContext method(JsonArray chain){
        put("method", chain.encodePrettily())
    }

    APICallContext method(String method){
        put("method", method)
        return this
    }

    String method(){
        return getString("method")
    }

    APICallContext cellAddress(String address){
        put("cellAddress", address)
        return this
    }

    APICallContext serviceType(type){
        put "serviceType", type
    }

    String serviceType(){
        return getString("serviceType")
    }
}
