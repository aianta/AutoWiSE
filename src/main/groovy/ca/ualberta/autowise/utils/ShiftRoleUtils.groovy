package ca.ualberta.autowise.utils


import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import ca.ualberta.autowise.model.ShiftRole

import groovy.transform.Field

import org.slf4j.LoggerFactory




@Field static log = LoggerFactory.getLogger(ShiftRoleUtils.class)
@Field static EVENT_STATUS_RANGE = "\'Event Status\'!A:C" //TODO -> move to a constants file? See about testing

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
    log.info "shiftRoleString: ${shiftRoleString}"
    log.info "roleName: ${roleName}"
    Role role = roles.stream().filter {role->role.name.equals(roleName)}
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
