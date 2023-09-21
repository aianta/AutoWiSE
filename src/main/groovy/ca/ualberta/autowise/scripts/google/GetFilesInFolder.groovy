package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import groovy.transform.Field
import io.vertx.core.Promise
import org.slf4j.LoggerFactory

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.GetFilesInFolder.class)

/**
 * @param googleAPI Google API object to use when making request
 * @param fileId id of the file to retrieve
 * @return a promise that completes with a {@link com.google.api.services.drive.model.File} object or fails with an exception.
 */
static def getFile(GoogleAPI googleAPI, fileId){
    Promise promise = Promise.promise()
    try{
        File target = googleAPI.drive().files().get(fileId).setFields("id,name,kind,mimeType,webViewLink").execute()
        promise.complete(target)
    }catch(GoogleJsonResponseException e){
        GoogleJsonError error = e.getDetails();
        log.error e.getMessage(), e
        promise.fail(e)
    }
    return promise.future()
}


/**
 * Retrieves all files in a certain folder of a given type.
 * @param googleAPI GoogleAPI object to use when making the request
 * @param folderId The parent folder Id. See <a href="https://robindirksen.com/blog/where-do-i-get-google-drive-folder-id">blog post</a> for how to find folder ids in google drive.
 * @param mimeType The mimeTypes of the files to fetch. See <a href="https://developers.google.com/drive/api/guides/mime-types">Drive API documentation</a> for a list of supported mimeTypes.
 */
static def getFiles(googleAPI, folderId, mimeType ){
    return _getFiles(googleAPI, "'${folderId}' in parents and mimeType = '${mimeType}'");
}

/**
 * @param googleAPI GoogleAPI object to use when making the request
 * @param folderId The parent folder Id. See <a href="https://robindirksen.com/blog/where-do-i-get-google-drive-folder-id">blog post</a> for how to find folder ids in google drive.
 * @return A list of all files inside the specified parent folder.
 */
static def getFiles(googleAPI, folderId ){
    return _getFiles(googleAPI, "'${folderId}' in parents")
}

/**
 * Internal method used to fulfill different types of fetch requests
 * @param googleAPI GoogleAPI object to use when making the request
 * @param query Search & filter options, see: <a href="https://developers.google.com/drive/api/guides/search-files">Drive API Search Files Documentation</a>
 * @return A list of all files matching the given query.
 */
private static def _getFiles(GoogleAPI googleAPI, query){
    Promise promise = Promise.promise();
    try{
        def final PAGE_SIZE = 35 //Files to fetch at once.

        //Store fetched files in results list
        def result = new ArrayList<File>()

        //Make an initial request for the files
        FileList fileList = googleAPI.drive().files()
                .list()
                .setQ(query)
                .setPageSize(PAGE_SIZE)
                .execute()

        result.addAll(fileList.getFiles())

        //If there are more files to fetch, get them too.
        while(fileList.getNextPageToken() != null){
            fileList = googleAPI.drive().files()
                    .list()
                    .setQ(query)
                    .setPageSize(PAGE_SIZE)
                    .setPageToken(fileList.getNextPageToken())
                    .execute()

            result.addAll(fileList.getFiles())

        }

        promise.complete(result)
    }catch(GoogleJsonResponseException e){
        GoogleJsonError error = e.getDetails();
        log.error e.getMessage(), e
        promise.fail(e)
    }

    return promise.future()
}




