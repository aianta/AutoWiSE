package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.SQLite
import ca.ualberta.autowise.model.ContactStatus
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.Volunteer
import ca.ualberta.autowise.model.WaitlistEntry
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import org.slf4j.LoggerFactory

import java.time.ZonedDateTime
import java.util.stream.Collectors

import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.appendAt
import static ca.ualberta.autowise.scripts.google.UpdateSheetValue.updateAt
import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValuesAt


@Field static def VOLUNTEER_CONTACT_STATUS_RANGE = "\'Volunteer Contact Status\'!A:H"
@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.ManageEventVolunteerContactSheet.class)


static def getWaitlistForShiftRole(SQLite db, UUID eventId, shiftRoleString){

    return db.getEventContactStatusTable(eventId).compose {
        List<WaitlistEntry> result = it.stream().filter {it.desiredShiftRole.equals(shiftRoleString) && it.status.equals("Waitlisted") && it.waitlistedOn != null}
            .map {new WaitlistEntry(email: it.volunteerEmail, waitlistedOn: it.waitlistedOn, desiredShiftRole: it.desiredShiftRole)}
            .collect(Collectors.toList())

        result = result.sort((e1,e2)->{
            return e1.waitlistedOn.compareTo(e2.waitlistedOn)
        })

        return Future.succeededFuture(result);
    }


}

/**
 *
 * @param db
 * @param eventId
 * @param volunteerEmail
 * @return true if the volunteer has already signed up for a different shift-role.
 */
static def hasVolunteerAlreadySignedUp(SQLite db, UUID eventId, volunteerEmail){

    return db.getVolunteerContactStatus(eventId, volunteerEmail)
        .compose {
            return Future.succeededFuture(it.acceptedOn != null)
        }

}

/**
 *
 * @param db
 * @param eventId
 * @param volunteerEmail
 * @return true if the volunteer has already cancelled a shift-role or rejected the opportunity entirely.
 */
static def hasVolunteerAlreadyCancelled(SQLite db, UUID eventId, volunteerEmail){

    return db.getVolunteerContactStatus(eventId, volunteerEmail).compose {
        return Future.succeededFuture(it.cancelledOn != null)
    }


}

static def updateVolunteerStatus(SQLite db, UUID eventId, sheetId, volunteerEmail, volunteerStatus, shiftRoleString){

    return db.getVolunteerContactStatus(eventId, volunteerEmail).compose {
        it.status = volunteerStatus
        it.desiredShiftRole = shiftRoleString

        switch (volunteerStatus){
            case "Accepted":
                it.acceptedOn = ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone);
                break;
            case "Rejected":
                it.rejectedOn = ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone);
                break;
            case "Cancelled":
                it.cancelledOn = ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone);
                break;
            case "Waitlisted":
                it.waitlistedOn = ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone)
                break;
            case "Waiting for response":
                it.lastContacted = ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone)
                break;
            default:
                log.warn "Unrecognized volunteer status string!"
        }

        return db.updateVolunteerContactStatus(it);

    }





}

static Future<List<ContactStatus>> syncEventVolunteerContactSheet(SQLite db, UUID eventId, String sheetId, wiserVolunteers){

    db.getEventContactStatusTable(eventId).compose {
        def stringSetVolunteers = wiserVolunteers.stream().map(volunteer -> volunteer.email).collect(Collectors.toSet());
        def volunteerContactStatusSet = [] as Set
        def toAdd = [] as Set

        it.forEach {volunteerContactStatusSet.add(it.volunteerEmail)}

        //Find all volunteers that appear in the set of all wiser volunteers but not in the contact status page
        for (String volunteer: stringSetVolunteers){
            if(!volunteerContactStatusSet.contains(volunteer)){
                toAdd.add(volunteer)
            }
        }

        List<ContactStatus> newContactStatuses = new ArrayList<>();
        toAdd.forEach {
            ContactStatus cs = new ContactStatus();
            cs.volunteerEmail = it;
            cs.eventId = eventId;
            cs.sheetId = sheetId;
            cs.status = "Not Contacted"
            cs.lastContacted = null;
            cs.acceptedOn = null;
            cs.rejectedOn = null;
            cs.cancelledOn = null;
            cs.waitlistedOn = null;
            cs.desiredShiftRole = null;

            newContactStatuses.add(cs);
        }

        CompositeFuture.all(newContactStatuses.stream().map {db.insertContactStatus(it)}
            .collect(Collectors.toList())).compose{
            return db.getEventContactStatusTable(eventId)
        }




    }


}


static def updateVolunteerContactStatusTable(services, Event event){

    return generateVolunteerContactStatusTable(services, event).compose {
        return updateAt(services.googleAPI, event.sheetId, VOLUNTEER_CONTACT_STATUS_RANGE, it)
    }

}

static def generateVolunteerContactStatusTable(services, Event event){
    return ((SQLite)services.db).getEventContactStatusTable(event.id).compose {

        List<List<String>> tableData = new ArrayList<>();
        tableData.add(makeHeaderRow());

        it.forEach { contactStatus ->
            tableData.add([
                    contactStatus.volunteerEmail,
                    contactStatus.lastContacted == null ? "" : contactStatus.lastContacted.format(EventSlurper.eventTimeFormatter),
                    contactStatus.status,
                    contactStatus.acceptedOn == null ? "" : contactStatus.acceptedOn.format(EventSlurper.eventTimeFormatter),
                    contactStatus.rejectedOn == null ? "" : contactStatus.rejectedOn.format(EventSlurper.eventTimeFormatter),
                    contactStatus.cancelledOn == null ? "" : contactStatus.cancelledOn.format(EventSlurper.eventTimeFormatter),
                    contactStatus.waitlistedOn == null ? "" : contactStatus.waitlistedOn.format(EventSlurper.eventTimeFormatter),
                    contactStatus.desiredShiftRole
            ])
        }

        ValueRange valueRange = new ValueRange();
        valueRange.setValues(tableData)
        valueRange.setRange(VOLUNTEER_CONTACT_STATUS_RANGE)
        valueRange.setMajorDimension("ROWS")

        return Future.succeededFuture(valueRange)
    }
}

static def makeHeaderRow(){
    return ["Volunteer","Last Contacted", "Status","Accepted On","Rejected On","Cancelled On","Waitlisted On","Desired Shift - Role"]
}
