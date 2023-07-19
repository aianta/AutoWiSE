package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.ShiftRole
import ca.ualberta.autowise.scripts.google.EventSlurper

import java.util.stream.Collectors

import static ca.ualberta.autowise.scripts.FindAvailableShiftRoles.*


static def buildShiftRoleOptions(List<ShiftRole> shiftRoles){
    StringBuilder sb = new StringBuilder()
    sb.append("<table><thead><tr><th>Role</th><th>Start Time</th><th>End Time</th><th>Description</th><th>Volunteer Link</th></tr></thead>")

    def it = shiftRoles.listIterator()
    if(it.hasNext()){
        def curr = it.next()
        sb.append("<tr>" +
                "<td>${curr.role.name}</td>" +
                "<td>${curr.shift.startTime.format(EventSlurper.shiftTimeFormatter)}</td>" +
                "<td>${curr.shift.endTime.format(EventSlurper.shiftTimeFormatter)}</td>" +
                "<td>${curr.role.description}</td>" +
                "<td><a href=\"http://localhost:8080/${curr.acceptHook.path()}\">Click to Volunteer!</a></td>" +
                "</tr>")
    }
    sb.append("</table>")
    return sb.toString()
}
