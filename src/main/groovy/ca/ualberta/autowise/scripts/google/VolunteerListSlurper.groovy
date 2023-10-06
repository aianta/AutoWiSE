package ca.ualberta.autowise.scripts.google


import ca.ualberta.autowise.GoogleAPI
import ca.ualberta.autowise.model.Volunteer
import groovy.transform.Field
import io.vertx.core.Future
import io.vertx.core.Promise
import org.slf4j.LoggerFactory
import static ca.ualberta.autowise.scripts.google.GetSheetValue.*


@Field static log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.google.VolunteerListSlurper.class)

static def getVolunteerByEmail(String email, Set<Volunteer> volunteers){
    return volunteers.stream().filter (volunteer->volunteer.email.equals(email))
            .findFirst().orElse(null);
}

static def slurpVolunteerList(GoogleAPI googleAPI, volunteerSheetId, volunteerTableRange){

    //Store as a set for easy 'contains' operations
    Set<Volunteer> volunteers = new ArrayList<>()

            return getValuesAt(googleAPI, volunteerSheetId, volunteerTableRange)
            .compose {data->

                try{
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

                    return Future.succeededFuture(volunteers)
                }catch (Exception e){
                    log.error "Error fetching volunteer list"
                    log.error e.getMessage(), e
                    return Future.failedFuture(e)
                }
            }
}

