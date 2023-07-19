package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import ca.ualberta.autowise.model.ShiftRole
import com.google.api.services.sheets.v4.model.ValueRange
import groovy.transform.Field
import org.slf4j.LoggerFactory

import static ca.ualberta.autowise.scripts.google.GetSheetValue.getValuesAt

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.FindAvailableShiftRoles.class)
@Field static EVENT_STATUS_RANGE = "\'Event Status\'!A:B" //TODO -> move to a constants file? See about testing


static def findAvailableShiftRoles(GoogleAPI googleAPI, sheetId ){

    def data = getValuesAt(googleAPI, sheetId, EVENT_STATUS_RANGE)
    def it = data.listIterator();
    def tableFirstColHeader = "Shift - Role"

    Set<String> unfilledShiftRoles = [] as Set

    while(it.hasNext()){
        def rowData = it.next();
        if (rowData.isEmpty() || !rowData.get(0).equals(tableFirstColHeader)){
            continue //Skip all lines until 'Shift - Role' header
        }

        if (rowData.get(0).equals(tableFirstColHeader)){
            rowData = it.next() //Advance to the first record
        }

        //Check if shift-role has been filled.
        if (rowData.size() == 1 || rowData.get(1).equals("") || rowData.get(1).equals("-")){
            //If there is no value beside this shiftRoleString, or the value is empty, or a dash the shift-role has not been filled.
            unfilledShiftRoles.add(rowData.get(0))
        }
    }

    return unfilledShiftRoles
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
    return shiftRoleComponents
}