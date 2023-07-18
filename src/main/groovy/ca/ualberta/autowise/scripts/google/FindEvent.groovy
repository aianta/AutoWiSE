package ca.ualberta.autowise.scripts.google


import com.google.api.services.drive.model.File
import ca.ualberta.autowise.AutoWiSE
import static ca.ualberta.autowise.scripts.google.GetFilesInFolder.getFiles
import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValueAt
import static ca.ualberta.autowise.scripts.google.EventSlurper.slurpSheet
import static ca.ualberta.autowise.scripts.google.EventSlurper.isSlurpable

static def findEvent (services, eventId){
    List<File> files = getFiles(services.googleAPI, AutoWiSE.config.getString("autowise_drive_folder_id"), "application/vnd.google-apps.spreadsheet")

    files.forEach {f->
        if (f.getName().startsWith(AutoWiSE.config.getString("autowise_event_prefix"))){
            //TODO - batch these
            def status = getValueAt(services.googleAPI, f.getId(), "Event!A3")
            def currId = getValueAt(services.googleAPI, f.getId(), "Event!A2")
            if (UUID.fromString(currId).equals(eventId) && isSlurpable(status)){
                return slurpSheet(services.googleAPI, f.getId())
            }
        }
    }
}
