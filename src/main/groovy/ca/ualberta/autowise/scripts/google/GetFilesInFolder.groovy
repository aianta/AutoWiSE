package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.APICallContext
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import groovy.transform.Field
import io.vertx.core.Future
import org.slf4j.LoggerFactory

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.GetFilesInFolder.class)
@Field static final PAGE_SIZE = 35 //Files to fetch at once

/**
 * @param googleAPI Google API object to use when making request
 * @param fileId id of the file to retrieve
 * @return a promise that completes with a {@link com.google.api.services.drive.model.File} object or fails with an exception.
 */

static def getFile(GoogleAPI googleAPI, fileId){

    APICallContext callContext = new APICallContext()
    callContext.docId(fileId)
    return googleAPI.drive (callContext, {it.files().get(fileId).setFields("id,name,kind,mimeType,webViewLink")})

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

        //Store fetched files in results list
        def result = new ArrayList<File>()

        APICallContext context = new APICallContext()
        context.put("query", query)
                .put("note", "initial request for files")

        //Make an initial request for the files
        return googleAPI.<FileList>drive(context, {it.files().list().setPageSize(PAGE_SIZE).setQ(query)})
                .compose {fileList->
                    //Recursively handle pagination
                    return handleFilesResult(googleAPI, context, fileList, query, result)
                }

}

/**
 * FileList API is paginated, so we recursively handle fetching files until we run out of result pages.
 * @param googleAPI GoogleAPI object with which to make requests for the next page of results
 * @param lastContext the {@link APICallContext} for the previous request.
 * @param fileList the file list produced by the last request
 * @param query the query for the fileList API
 * @param result A list to be populated with all the retrieved files.
 * @return A future that succeeds when no 'nextPageToken' is returned by the FileList API.
 */
private static Future handleFilesResult(GoogleAPI googleAPI, APICallContext lastContext, FileList fileList, query, ArrayList<File> result ){
    result.addAll(fileList.getFiles())
    if(fileList.getNextPageToken() != null){
        APICallContext nextContext = new APICallContext()
        nextContext.put "relatedContext", lastContext.id.toString()
        nextContext.put "note", "getting next page of files"
        return googleAPI.<FileList>drive(nextContext, {it
                .files()
                .list()
                .setPageToken(fileList.getNextPageToken())
                .setQ(query)
                .setPageSize(PAGE_SIZE)
        }).compose {return handleFilesResult(googleAPI, nextContext, it, query, result)}
    }else{
        return Future.succeededFuture(result)
    }
}




