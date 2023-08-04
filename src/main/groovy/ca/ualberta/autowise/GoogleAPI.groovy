package ca.ualberta.autowise

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
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

/**
 * @Author Alexandru Ianta
 * Gives scripts access to GoogleAPI.
 * Handles authentication.
 */
class GoogleAPI {

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

    def credentials
    def _drive
    def _docs
    def _sheets
    def _gmail

    static GoogleAPI createInstance(applicationName, credentialsPath, tokensDirPath, authServerHost, authServerPort){
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
        ).setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
        .setAccessType("offline")
        .build()

        def authServerReceiver = new LocalServerReceiver.Builder().setHost(AUTH_SERVER_HOST).setPort(AUTH_SERVER_PORT).build()
        def credentials = new AuthorizationCodeInstalledApp(authFlow, authServerReceiver).authorize("user")

        instance = new GoogleAPI(credentials)
    }

    static GoogleAPI getInstance(){
        if (instance == null){
            throw new RuntimeException("Cannot get GoogleAPI instance because it was not created!")
        }
        return instance
    }

    private GoogleAPI(credentials){
        this.credentials = credentials
    }

    Drive drive(){
        if (_drive == null) {
            _drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME).build()
        }
        return _drive
    }

    Sheets sheets(){
        if(_sheets == null){
            _sheets = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME).build()
        }
        return _sheets
    }

    Gmail gmail(){
        if(_gmail == null){
            _gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME).build()
        }
        return _gmail
    }

    Docs docs(){
        if(_docs == null){
            _docs = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME).build()
        }
        return _docs
    }

}
