package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import ca.ualberta.autowise.model.ShiftRole
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import io.vertx.core.Future
import io.vertx.core.Promise
import org.slf4j.LoggerFactory



import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValuesAt

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.FindAvailableShiftRoles.class)
@Field static EVENT_STATUS_RANGE = "\'Event Status\'!A:C" //TODO -> move to a constants file? See about testing


static def findAvailableShiftRoles(GoogleAPI googleAPI, sheetId ){
    log.info "trying to find available shift roles!"
    return getValuesAt(googleAPI, sheetId, EVENT_STATUS_RANGE).compose(
            data->{

                log.info "Got ${data.size()} rows from ${EVENT_STATUS_RANGE}"

                def it = data.listIterator();
                def tableFirstColHeader = "Shift - Role"

                Set<String> unfilledShiftRoles = [] as Set

                while(it.hasNext()) {
                    def rowData = it.next();
                    log.info rowData.toString()
                    if (rowData.isEmpty() || !rowData.get(0).equals(tableFirstColHeader)) {
                        log.info "Skipping row before shift-role header: ${!rowData.isEmpty()?rowData.get(0).equals(tableFirstColHeader):"empty"}"
                        continue //Skip all lines until 'Shift - Role' header
                    }
                    if (rowData.get(0).equals(tableFirstColHeader)){
                        break
                    }
                }

                while (it.hasNext()){
                    def rowData = it.next()

                    //Check if shift-role has been filled.
                    if (rowData.size() == 1 || rowData.get(1).equals("") || rowData.get(1).equals("-")){
                        //If there is no value beside this shiftRoleString, or the value is empty, or a dash the shift-role has not been filled.
                        unfilledShiftRoles.add(rowData.get(0))
                        log.info "Found unfilled shift-role! ${rowData.get(0)}"
                    }else{
                        log.info "Shift-role ${rowData.get(0)} has been filled!"
                    }

                }
                return Future.succeededFuture(unfilledShiftRoles)
            }
    )
}


static def getRoleName(shiftRoleString){
    return parseShiftRoleString(shiftRoleString)[1]
}

/**
 *
 * @param shiftRoleString a string with format '<shift-index> - <role-name>', generally found in 'Event Status' sheet.
 * @param roles the roles of an event.
 * @return The ShiftRole object corresponding with the provided shift role string
 */
static def getShiftRole(shiftRoleString, List<Role> roles){

    /**
     * Parse shift-role string. (ex: 1 - Grill Master)
     * Scheme: <shift-index> - <role-name>
     */
    def shiftRoleComponents = parseShiftRoleString(shiftRoleString)
    def shiftIndex = Integer.parseInt(shiftRoleComponents[0].trim())

    def roleName = shiftRoleComponents[1].trim()

    Role role = roles.stream().filter {role->role.getName().equals(roleName)}
                .findFirst().orElse(null)

    if (role == null){
        log.error "Could not find role with name: ${roleName}"
        return null
    }

    Shift shift = role.shifts.stream().filter { shift->shift.index == shiftIndex}.findFirst().orElse(null)

    if (shift == null){
        log.error "Could not find shift with index ${shiftIndex} for roleName: ${roleName}"
        return null
    }

    return new ShiftRole(shift: shift, role: role, shiftRoleString: shiftRoleString)
}

private static def parseShiftRoleString(shiftRoleString){
    /**
     * Parse shift-role string. (ex: 1 - Grill Master)
     * Scheme: <shift-index> - <role-name>
     */
    def shiftRoleComponents = shiftRoleString.split("-")

    //Support role names with hyphens by only grabbing the shift number before the first hyphen, and then using the entire rest of the shift role string as the role name.
    List<String> result = new ArrayList<>();
    result.add(shiftRoleComponents[0])
    result.add(shiftRoleString.substring(shiftRoleString.indexOf('-') + 1, shiftRoleString.length()))

    return result
}
