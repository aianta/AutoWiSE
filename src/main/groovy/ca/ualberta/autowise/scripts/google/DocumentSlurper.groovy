package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.GoogleAPI
import com.google.api.services.docs.v1.model.Document
import com.google.api.services.docs.v1.model.ParagraphElement
import com.google.api.services.docs.v1.model.StructuralElement
import com.google.api.services.docs.v1.model.TableCell
import com.google.api.services.docs.v1.model.TableRow
import com.google.api.services.docs.v1.model.TextRun
import groovy.transform.Field
import org.slf4j.LoggerFactory

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.DocumentSlurper.class)

/**
 *
 * Based on the example provided in the google docs API documentation here:
 * <a href="https://developers.google.com/docs/api/samples/extract-text"></a>
 */
static def slurpDocument(GoogleAPI googleAPI, documentId){

    Document doc = googleAPI.docs().documents().get(documentId).execute()
    return readStructuralElements(doc.getBody().getContent())

}

private static def readParagraphElement(element){
    TextRun run = element.getTextRun();
    if (run == null || run.getContent() == null){
        return ""
    }
    return run.getContent()
}


private static def readStructuralElements(elements){
    StringBuilder sb = new StringBuilder()
    for (StructuralElement element: elements){
        if (element.getParagraph() != null){
            for (ParagraphElement paragraphElement: element.getParagraph().getElements()){
                sb.append(readParagraphElement(paragraphElement))
            }
        }else if (element.getTable() != null){
            for (TableRow row: element.getTable().getTableRows()){
                for (TableCell cell: row.getTableCells()){
                    // Recursion here could theoretically stackoverflow
                    sb.append(readStructuralElements(cell.getContent()))
                }
            }
        }else if (element.getTableOfContents() != null){
            sb.append(readStructuralElements(element.getTableOfContents().getContent()))
        }
    }
    return sb.toString();
}
