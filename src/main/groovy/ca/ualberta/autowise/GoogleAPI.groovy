package ca.ualberta.autowise

import ca.ualberta.autowise.model.APICallContext
import ca.ualberta.autowise.model.SlackBrowser
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpRequest
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.docs.v1.Docs
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import java.time.Instant
import java.util.function.Function


import static ca.ualberta.autowise.scripts.slack.BlockingSlackMessage.*

/**
 * @Author Alexandru Ianta
 * Gives scripts access to GoogleAPI.
 * Handles authentication.
 */
class GoogleAPI {
    static final long MAXIMUM_BACKOFF = 128000 //128s in ms.
    static final int MAX_ATTEMPTS = 10 //Maximum number of retries before we give up on a service call.

    static log = LoggerFactory.getLogger(GoogleAPI.class)
    static JSON_FACTORY = GsonFactory.getDefaultInstance()
    static HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    static TOKENS_DIRECTORY_PATH = "tokens"
    static List<String> SCOPES = [
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE,
            DriveScopes.DRIVE_FILE,
            DriveScopes.DRIVE_METADATA,
            DocsScopes.DOCUMENTS,
            DocsScopes.DRIVE,
            DocsScopes.DRIVE_FILE,
            GmailScopes.GMAIL_SEND
    ]

    //Some default values, these are overwritten in createInstance()
    static CREDENTIALS_PATH = "credentials.json"
    static APPLICATION_NAME = "AutoWiSE"
    static AUTH_SERVER_PORT = 8888
    static AUTH_SERVER_HOST = "localhost"

    static instance = null

    Credential credentials
    def _drive
    def _docs
    def _sheets
    def _gmail
    SQLite db
    Vertx vertx
    SlackAPI slackAPI
    JsonObject config;

    static GoogleAPI createInstance(
            SlackBrowser browser,
            SQLite db,
            vertx,
            slackAPI,
            config
    ){
        APPLICATION_NAME = config.getString("application_name")
        CREDENTIALS_PATH =  config.getString("credentials_path")
        TOKENS_DIRECTORY_PATH =  config.getString("auth_tokens_directory_path")
        AUTH_SERVER_HOST =  config.getString("auth_server_host")
        AUTH_SERVER_PORT =  config.getInteger("auth_server_receiver_port")

        def credentialsStream = new FileInputStream(new java.io.File(CREDENTIALS_PATH))
        def clientSecrets = GoogleClientSecrets.load JSON_FACTORY, new InputStreamReader(credentialsStream)
        def authFlow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT,
                JSON_FACTORY,
                clientSecrets,
                SCOPES
        )
        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
        .setAccessType("offline")
        .build()

        def authServerReceiver = new LocalServerReceiver.Builder().setHost(AUTH_SERVER_HOST).setPort(AUTH_SERVER_PORT).build()
        def credentials = new AuthorizationCodeInstalledApp(authFlow, authServerReceiver, browser).authorize("user")
        instance = new GoogleAPI(credentials, db, vertx, slackAPI, config)
    }

    static GoogleAPI getInstance(){
        if (instance == null){
            throw new RuntimeException("Cannot get GoogleAPI instance because it was not created!")
        }
        return instance
    }

    private GoogleAPI(credentials, SQLite db, vertx, slackAPI, config){
        this.credentials = credentials
        this.db = db
        this.vertx = vertx
        this.slackAPI = slackAPI
        this.config = config
    }

    private void validateCredentials(){
        try{
            log.info "Validating credentials";
            log.info "credentials expiry: ${credentials.getExpirationTimeMilliseconds()}"
            log.info "current time: ${Instant.now().toEpochMilli()}"
            if(Instant.now().toEpochMilli() >= credentials.getExpirationTimeMilliseconds()){
                def refreshed = credentials.refreshToken()
                log.info "token refreshed: ${refreshed}"
                if (!refreshed){
                    throw new RuntimeException("Failed to refresh authentication token.")
                }
            }
        }catch(Exception e){
            log.error e.getMessage(), e
        }

    }


    Drive drive(){
        validateCredentials()
        if (_drive == null) {
            _drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME).build()
        }
        return _drive
    }

    Sheets sheets(){
        validateCredentials()
        if(_sheets == null){
            _sheets = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME).build()
        }
        return _sheets
    }

    Gmail gmail(){
        validateCredentials()
        if(_gmail == null){
            _gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME).build()
        }
        return _gmail
    }

    Docs docs(){
        validateCredentials()
        if(_docs == null){
            _docs = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME).build()
        }
        return _docs
    }

    <T> Future<T> sheets(APICallContext context, Function<Sheets, AbstractGoogleClientRequest<T>> apiCall){
        context.serviceType("sheets")
        return service(sheets(), apiCall, context)
    }

    <T> Future<T> gmail(APICallContext context, Function<Gmail, AbstractGoogleClientRequest<T>> apiCall){
        context.serviceType("gmail")
        return service(gmail(), apiCall, context)
    }

    <T> T syncDocs (APICallContext context, Function<Docs, AbstractGoogleClientRequest<T>> apiCall){
        context.serviceType("docs")
        return syncService(docs(), apiCall, context)
    }

    <T> Future<T> docs(APICallContext context, Function<Docs, AbstractGoogleClientRequest<T>> apiCall){
        context.serviceType("docs")
        return service(docs(), apiCall, context)
    }

    <T> Future<T> drive(APICallContext context, Function<Drive, AbstractGoogleClientRequest<T>> apiCall){
        context.serviceType("drive")
        return service(drive(), apiCall, context)
    }

    /**
     * Sync version {@link GoogleAPI#service}, should only be used where async is not possible. At time of writing
     * this is only for email template resolution.
     *
     * TODO: There is probably some refactoring that could remove duplicate code between this method and the service method.
     *
     * @param service
     * @param serviceCall
     * @param context
     * @return
     */
    <R,S> R syncService(S service, Function<S, AbstractGoogleClientRequest<R>> serviceCall, APICallContext context ){

        //Number of milliseconds to wait before making the service call. Used for exponential backoff when handling 429 errors
        long waitTime = context.attempt() - 1 == 0? 0: backoffWaitTime(context.attempt())

        def request = serviceCall.apply(service)
        Instant start = Instant.now()
        try{
            context.put "requestUrl", request.buildHttpRequestUrl().toString()

            HttpRequest h = request.buildHttpRequest()
            if(h.getContent() != null){ // If there is content to this request
                //Lets try to get the request content saved for debugging.
                ByteArrayOutputStream out = new ByteArrayOutputStream()
                h.getContent().writeTo(out)
                def contentString = out.toString()
                context.put "requestContent", contentString
            }

            if (waitTime > 0){ //Backoff making the request if necessary
                Thread.sleep(waitTime)
            }
            //Now let's make that request
            def result = request.execute()
            return result;
        }
        catch (UnknownHostException e){ //Handle no internet
            context.put "errorNote", "this could happen if there is no internet access."
            context.error(e)
            log.error e.getMessage(),e


        }catch (GoogleJsonResponseException e){
            context.error(e)
            GoogleJsonError error = e.getDetails();
            log.error e.getMessage(), e

            /**
             * https://developers.google.com/drive/api/guides/handle-errors
             */
            switch (error.getCode()){
                case 400: //Bad request
                    log.error "400 - Bad Request: Google API error while making service call.\n${context.encodePrettily()}"
                    sendSlackMessage(slackAPI, config.getString("technical_channel"), "400 - Bad Request: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                    break;
                case 401: //Unauthorized - invalid credentials
                    log.error "401 - Unauthorized: Google API error while making service call.\n${context.encodePrettily()}"
                    sendSlackMessage(slackAPI, config.getString("technical_channel"), "401 - Unauthorized: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                    break;
                case 403: //Forbidden - insufficient permissions
                    log.error "403 - Forbidden: Google API error while making service call.\n${context.encodePrettily()}"
                    sendSlackMessage(slackAPI, config.getString("technical_channel"), "403 - Forbidden: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                    break;
                case 404: //Not Found
                    log.error "404 - Not Found: Google API error while making service call.\n${context.encodePrettily()}"
                    sendSlackMessage(slackAPI, config.getString("technical_channel"), "404 - Not Found: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                    break;
                case 429: //Too Many Requests
                    log.error("Got error code 429 - Too Many Requests from google API.")
                    context.attempt(context.attempt() + 1) //Increment the number of attempts
                    if(context.attempt() > MAX_ATTEMPTS){
                        log.error("Maximum number of attempts reached. Service call failed.")
                        sendSlackMessage(slackAPI, config.getString("technical_channel"), "429 - Too Many Requests: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                    }else{
                        log.error "Retrying service call."
                        return syncService(service, serviceCall, context)
                    }
                    break;
                    //Internal server errors on google's side
                case 500:
                case 502:
                case 503:
                case 504:
                    log.error "50x - Internal Server Error: Google API error while making service call.\n${context.encodePrettily()}"
                    sendSlackMessage(slackAPI, config.getString("technical_channel"), "50x - Internal Server Error: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                    break;

            }


        }catch (SocketTimeoutException e){
            log.error (e.getMessage(),e)
            context.attempt(context.attempt() + 1) //Increment number of attempts
            if(context.attempt() > MAX_ATTEMPTS){
                log.error("Maximum number of attempts reached. Service call failed.")
                sendSlackMessage(slackAPI, config.getString("technical_channel"), "SocketTimeoutException: maximum number of attempts (" + context.attempt() + ") reached! \n```\n${context.encodePrettily()}}\n```\n" )

            }else{
                log.error("Retrying service call")
                return syncService(service, serviceCall, context)
            }
        }catch (Exception e){
            context.error(e)
            log.error(e.getMessage(), e)
            sendSlackMessage(slackAPI, config.getString("technical_channel"), "Unknown error while making service call. ```\n${context.encodePrettily()}\n```" )
        } finally {
            Instant end = Instant.now()
            long serviceCallDuration = end.toEpochMilli() - start.toEpochMilli()
            context.duration(serviceCallDuration)
            log.info "saving call context"
            db.saveAPICallContext(context)
        }

    }

    /**
     * Wrapper function for API calls that ensures:
     * <ul>
     *     <li>Uniform handling of Google API  errors</li>
     *     <li>Record keeping of all api calls and any errors that arose</li>
     *     <li>API calls are executed in a separate thread to avoid blocking the main loop.</li>
     * </ul>
     * @param service The google service to make the serviceCall with.
     * @param serviceCall A function that given a google service, returns an {@link AbstractGoogleClientRequest}
     * @param context A specialized {@link io.vertx.core.json.JsonObject} extended by helper class {@link APICallContext} containing
     * contextual information about the api call to be stored for record keeping purposes.
     * @return A future with the type R where R is the type of the expected result from the serviceCall being invoked.
     */
    <R,S> Future<R> service(S service, Function<S, AbstractGoogleClientRequest<R>> serviceCall, APICallContext context){

        //Number of milliseconds to wait before making the service call. Used for exponential backoff when handling 429 errors
        long waitTime = context.attempt() - 1 == 0? 0: backoffWaitTime(context.attempt())

        def request = serviceCall.apply(service)
        return vertx.<R>executeBlocking {
            Instant start = Instant.now()
            try{
                context.put "requestUrl", request.buildHttpRequestUrl().toString()

                HttpRequest h = request.buildHttpRequest()
                if(h.getContent() != null){ // If there is content to this request
                    //Lets try to get the request content saved for debugging.
                    ByteArrayOutputStream out = new ByteArrayOutputStream()
                    h.getContent().writeTo(out)
                    def contentString = out.toString()
                    context.put "requestContent", contentString
                }

                if (waitTime > 0){ //Backoff making the request if necessary
                    Thread.sleep(waitTime)
                }
                //Now let's make that request
                def result = request.execute()
                it.complete(result)
            }
            catch (UnknownHostException e){ //Handle no internet
                context.put "errorNote", "this could happen if there is no internet access."
                context.error(e)
                log.error e.getMessage(),e
                it.fail(e)

            }catch (GoogleJsonResponseException e){
                context.error(e)
                GoogleJsonError error = e.getDetails();
                log.error e.getMessage(), e

                /**
                 * https://developers.google.com/drive/api/guides/handle-errors
                 */
                switch (error.getCode()){
                    case 400: //Bad request
                        log.error "400 - Bad Request: Google API error while making service call.\n${context.encodePrettily()}"
                        sendSlackMessage(slackAPI, config.getString("technical_channel"), "400 - Bad Request: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                        break;
                    case 401: //Unauthorized - invalid credentials
                        log.error "401 - Unauthorized: Google API error while making service call.\n${context.encodePrettily()}"
                        sendSlackMessage(slackAPI, config.getString("technical_channel"), "401 - Unauthorized: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                        break;
                    case 403: //Forbidden - insufficient permissions
                        log.error "403 - Forbidden: Google API error while making service call.\n${context.encodePrettily()}"
                        sendSlackMessage(slackAPI, config.getString("technical_channel"), "403 - Forbidden: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                        break;
                    case 404: //Not Found
                        log.error "404 - Not Found: Google API error while making service call.\n${context.encodePrettily()}"
                        sendSlackMessage(slackAPI, config.getString("technical_channel"), "404 - Not Found: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                        break;
                    case 429: //Too Many Requests
                        log.error("Got error code 429 - Too Many Requests from google API.")
                        context.attempt(context.attempt() + 1) //Increment the number of attempts
                        if(context.attempt() > MAX_ATTEMPTS){
                            log.error("Maximum number of attempts reached. Service call failed.")
                            sendSlackMessage(slackAPI, config.getString("technical_channel"), "429 - Too Many Requests: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                            return it.fail(e) //Fail the service call if the maximum number of attempts has been reached.
                        }else{
                            log.error "Retrying service call."
                            return service(service, serviceCall, context)
                        }
                        break;
                    //Internal server errors on google's side
                    case 500:
                    case 502:
                    case 503:
                    case 504:
                        log.error "50x - Internal Server Error: Google API error while making service call.\n${context.encodePrettily()}"
                        sendSlackMessage(slackAPI, config.getString("technical_channel"), "50x - Internal Server Error: Google API error while making service call. ```\n${context.encodePrettily()}\n```" )
                        break;
                    default:
                        it.fail(e)
                }

                it.tryFail(e) //Really fail the service call, in case it somehow wasn't failed already
            }catch (SocketTimeoutException e){
                log.error (e.getMessage(),e)
                context.attempt(context.attempt() + 1) //Increment number of attempts
                if(context.attempt() > MAX_ATTEMPTS){
                    log.error("Maximum number of attempts reached. Service call failed.")
                    sendSlackMessage(slackAPI, config.getString("technical_channel"), "SocketTimeoutException: maximum number of attempts (" + context.attempt() + ") reached! \n```\n${context.encodePrettily()}}\n```\n" )
                    return it.fail(e)
                }else{
                    log.error("Retrying service call")
                    return service(service, serviceCall, context)
                }
            }catch (Exception e){
              context.error(e)
                log.error(e.getMessage(), e)
                sendSlackMessage(slackAPI, config.getString("technical_channel"), "Unknown error while making service call. ```\n${context.encodePrettily()}\n```" )
                it.fail(e)
            } finally {
                Instant end = Instant.now()
                long serviceCallDuration = end.toEpochMilli() - start.toEpochMilli()
                context.duration(serviceCallDuration)
                log.info "saving call context"
                db.saveAPICallContext(context)
            }
        }

    }

    /**
     * Implements wait time calculation for exponential backoff handling of 429 errors.
     * See link for more details.
     * https://developers.google.com/drive/api/guides/limits#exponential
     * @param numRetries
     * @return
     */
    private long backoffWaitTime(int numRetries){
        long minLong = 1L;
        long maxLong = 1000L;
        long randomMilli = minLong + (long) (Math.random() * (maxLong - minLong))
        return Math.min((Math.pow(2, numRetries)) + randomMilli, MAXIMUM_BACKOFF)
    }
}
