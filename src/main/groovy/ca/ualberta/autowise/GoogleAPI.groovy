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
import io.vertx.core.Promise
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory

import java.time.Instant
import java.util.function.Function


/**
 * @Author Alexandru Ianta
 * Gives scripts access to GoogleAPI.
 * Handles authentication.
 */
class GoogleAPI {
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

    static GoogleAPI createInstance(applicationName, credentialsPath, tokensDirPath, authServerHost, authServerPort, SlackBrowser browser, SQLite db, vertx){
        APPLICATION_NAME = applicationName
        CREDENTIALS_PATH = credentialsPath
        TOKENS_DIRECTORY_PATH = tokensDirPath
        AUTH_SERVER_HOST = authServerHost
        AUTH_SERVER_PORT = authServerPort

        def credentialsStream = new FileInputStream(new java.io.File(CREDENTIALS_PATH))
        def clientSecrets = GoogleClientSecrets.load JSON_FACTORY, new InputStreamReader(credentialsStream)
        def authFlow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT,
                JSON_FACTORY,
                clientSecrets,
                SCOPES
        )
//        .setDataStoreFactory(MemoryDataStoreFactory.getDefaultInstance())
        //TODO: If the memory data store factory works, then this is a permission issue with the file system and we have to talk to david to resolve it.
        // should do that before live deployment to minimize the number of times we need to auth through slack.
        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
        .setAccessType("offline")
        .build()

        def authServerReceiver = new LocalServerReceiver.Builder().setHost(AUTH_SERVER_HOST).setPort(AUTH_SERVER_PORT).build()
        def credentials = new AuthorizationCodeInstalledApp(authFlow, authServerReceiver, browser).authorize("user")
        instance = new GoogleAPI(credentials, db, vertx)
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

    static GoogleAPI getInstance(){
        if (instance == null){
            throw new RuntimeException("Cannot get GoogleAPI instance because it was not created!")
        }
        return instance
    }

    private GoogleAPI(credentials, SQLite db, vertx){
        this.credentials = credentials
        this.db = db
        this.vertx = vertx
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


    Future drive(Function<Drive, AbstractGoogleClientRequest> apiCall){
        Promise promise = Promise.promise();
        try{
            def request  = apiCall.apply(_drive)
            def result = request.execute()
            promise.complete(result)
        }catch(GoogleJsonResponseException e){
            GoogleJsonError error = e.getDetails();
            log.error e.getMessage(), e
            //TODO - google error handling
            promise.fail(e)
        }

        return promise.future()
    }

    <T> T drive2(Function<Drive, AbstractGoogleClientRequest<T>> apiCall){
        try{
            def request = apiCall.apply(_drive)
            def result = request.execute()

            return result
        }catch (GoogleJsonResponseException e){
            GoogleJsonError error = e.getDetails();
            log.error e.getMessage(), e
        }
    }

    <R,S> R service(S service, Function<S, AbstractGoogleClientRequest<R>> serviceCall){
        try{
            def request = serviceCall.apply(service)
            def result = request.execute()
            return result
        }catch (GoogleJsonResponseException e){
            GoogleJsonError error = e.getDetails();
            log.error e.getMessage(), e
        }
    }

    <R,S> Future<R> service2(S service, Function<S, AbstractGoogleClientRequest<R>> serviceCall){
        Promise promise = Promise.promise()
        try{
            def request = serviceCall.apply(service)
            def result = request.execute()
            promise.complete(result)
        }catch (GoogleJsonResponseException e){
            GoogleJsonError error = e.getDetails();
            log.error e.getMessage(), e
            promise.fail(e)
        }
        return promise.future()
    }

    <T> T drive3(Function<Drive,AbstractGoogleClientRequest<T>> apiCall){
        return service(_drive, apiCall)
    }

    <T> Future<T> drive4(Function<Drive, AbstractGoogleClientRequest<T>> apiCall){
        return service2(_drive, apiCall)
    }

    <R,S> Future<R> service3(S service, Function<S, AbstractGoogleClientRequest<R>> serviceCall, APICallContext context){
        Promise promise = Promise.promise()
        try{
            def request = serviceCall.apply(service)
            def result = request.execute()
            promise.complete(result)
        }catch (GoogleJsonResponseException e){
            GoogleJsonError error = e.getDetails();
            log.error e.getMessage(), e
            promise.fail(e)
        }
        return promise.future()
    }

    <T> Future<T> drive5(Function<Drive, AbstractGoogleClientRequest<T>> apiCall, APICallContext context){
        return service3(_drive, apiCall, context)

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
    <R,S> Future<R> service4(S service, Function<S, AbstractGoogleClientRequest<R>> serviceCall, APICallContext context){

        def request = serviceCall.apply(service)
        return vertx.<R>executeBlocking {
            try{
                def result = request.execute()
                it.complete(result)
            }catch (GoogleJsonResponseException e){
                GoogleJsonError error = e.getDetails();
                log.error e.getMessage(), e
                it.fail(e)
            }finally {
                db.saveAPICallContext(context)
            }
        }

    }
}
