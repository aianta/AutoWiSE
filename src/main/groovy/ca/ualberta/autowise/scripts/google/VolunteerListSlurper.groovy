package ca.ualberta.autowise.scripts.google

import ca.ualberta.autowise.AutoWiSE
import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.Volunteer
import groovy.transform.Field
import io.vertx.core.Future
import io.vertx.core.Promise
import org.slf4j.LoggerFactory

@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.VolunteerListSlurper.class)

static def getVolunteerByEmail(String email, Set<Volunteer> volunteers){
    return volunteers.stream().filter (volunteer->volunteer.email.equals(email))
            .findFirst().orElse(null);
}

static def slurpVolunteerList(GoogleAPI googleAPI, volunteerSheetId, volunteerTableRange){
    Promise promise = Promise.promise()
    //Store as a set for easy 'contains' operations
    Set<Volunteer> volunteers = new ArrayList<>()
    try{

        def response = googleAPI.sheets().spreadsheets().values().get(volunteerSheetId, volunteerTableRange).execute()
        def data = response.getValues();
        if(data == null || data.isEmpty()){
            return Future.failedFuture("Volunteer pool data missing from sheet ${volunteerSheetId}@${volunteerTableRange}")
        }

        ListIterator<List<Object>> it = data.listIterator()
        while(it.hasNext()){
            def currRow = it.next();
            if (currRow.isEmpty()){
                continue //Skip empty rows
            }

            if (currRow.get(0).equals("Name")){ //If the first column of the row contains 'Name' then this is the header row
                continue //Move on to the actual volunteers
            }

            Volunteer volunteer = new Volunteer(name: currRow.get(0), email: currRow.get(1))
            volunteers.add(volunteer)
        }

        promise.complete(volunteers)

    }catch (Exception e){
        log.error "Error fetching volunteer list!"
        log.error e.getMessage(), e
        promise.fail(e)
    }


    return promise.future()
}

