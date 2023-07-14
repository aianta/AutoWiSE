package ca.ualberta.autowise

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.drive.model.File;

class Globals{
    static JSON_FACTORY = GsonFactory.getDefaultInstance();
    static TOKENS_DIRECTORY_PATH = "tokens"

    static Collection<String> SCOPES = [
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE,
            DriveScopes.DRIVE_FILE,
            DriveScopes.DRIVE_METADATA,
            DriveScopes.DRIVE_METADATA_READONLY
    ]
    static CREDENTIALS_PATH = "credentials.json"
    static APPLICATION_NAME = "AutoWiSE"
}


static void main(String[] args) {
    println "Hello world!"

    NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    def SPREADSHEET_ID = "1iF2JvYvI_VUmhwraFdcgX9DuifPivjHfYykSsx4ms3Y"
    def CELL_ADDR = "Event!A1"

    def credentials = getCredentials(HTTP_TRANSPORT)
    println "Credentials"

    println credentials.toString()

//    def service = new Sheets.Builder(HTTP_TRANSPORT, Globals.JSON_FACTORY, credentials)
//        .setApplicationName(Globals.APPLICATION_NAME)
//        .build()

    def driveService =  new Drive.Builder(HTTP_TRANSPORT, Globals.JSON_FACTORY, credentials)
        .setApplicationName(Globals.APPLICATION_NAME).build()

    FileList result = driveService.files().list().setPageSize(10)
            //.setFields("nextPageToken, files(id, name)")
            .execute();
    List<File> files = result.getFiles()
    if (files == null || files.isEmpty()){
        println "No files found."
    }else{
        println "Files:"
        for (File file: files){
            //println file
            def params = [file.getName(), file.getId()]
            println "${params[0]} - ${params[1]}"
        }
    }

//
//    def response = service.spreadsheets().values().get(SPREADSHEET_ID, CELL_ADDR).execute();
//
//
//    def values = response.getValues();
//    if (values == null || values.isEmpty()){
//        println "No data found!"
//    }else{
//        println values.get(0).get(0)
//    }

}

 static Credential getCredentials(HTTP_TRANSPORT){
    def is = new FileInputStream(new java.io.File (Globals.CREDENTIALS_PATH))


    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load Globals.JSON_FACTORY, new InputStreamReader(is);
    println "Client Secrets:"

    println Globals.JSON_FACTORY

    // Build flow and trigger user authorization request.

    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT,
            Globals.JSON_FACTORY,
            clientSecrets,
            Globals.SCOPES
    )
            .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(Globals.TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build();
    LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
    return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

}