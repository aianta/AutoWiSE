package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.AutoWiSE
import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.Volunteer
import groovy.transform.Field
import org.slf4j.LoggerFactory

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.VolunteerListSlurper.class)

static def slurpVolunteerList(GoogleAPI googleAPI){
    //Store as a set for easy 'contains' operations
    Set<Volunteer> volunteers = new ArrayList<>()

    def volunteerSheetId = AutoWiSE.config.getString("autowise_volunteer_pool_id")
    def volunteerTableRange = AutoWiSE.config.getString("autowise_volunteer_table_range")

    def response = googleAPI.sheets().spreadsheets().values().get(volunteerSheetId, volunteerTableRange).execute()
    def data = response.getValues();
    if(data == null || data.isEmpty()){
        throw new RuntimeException("Volunteer pool data missing from sheet ${volunteerSheetId}@${volunteerTableRange}")
    }

    ListIterator<List<Object>> it = data.listIterator()
    while(it.hasNext()){
        def currRow = it.next();
        if (currRow.get(0).equals("Name")){ //If the first column of the row contains 'Name' then this is the header row
            continue //Move on to the actual volunteers
        }
        if (currRow.isEmpty()){
            continue //Skip empty rows
        }
        Volunteer volunteer = new Volunteer(name: currRow.get(0), email: currRow.get(1))
        volunteers.add(volunteer)
    }

    return volunteers
}

