package ca.ualberta

import ca.ualberta.autowise.SQLite
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import static TestConstants.dummyEventSheet
import static TestConstants.testDatabaseConnectionString;

@ExtendWith(VertxExtension.class)
class EventTest {
    private static final Logger log = LoggerFactory.getLogger(EventTest.class)
    static Vertx vertx = Vertx.vertx();
    static SQLite db = null;

    @BeforeAll
    static void setup(){

        log.info "Running setup"
        VertxTestContext context = new VertxTestContext();

        Promise sqlitePromise = Promise.promise();
        db = SQLite.createInstance(vertx, testDatabaseConnectionString, sqlitePromise.future())
        sqlitePromise.future()
                .onSuccess {
                    log.info "Test Database initialized"
                    context.completeNow()}
                .onFailure {context.failNow(it)}

        awaitContext(context)
    }

    @Test
    void insertEvent(){
        log.info "Running insert test"

        VertxTestContext testContext = new VertxTestContext();

        Event event = new Event()
        event.id = UUID.randomUUID();
        event.status = EventStatus.READY
        event.startTime = ZonedDateTime.now()
        event.endTime = ZonedDateTime.now()
        event.name = "Test Event"
        event.description = "A badly put together event"
        event.campaignStart = ZonedDateTime.now()
        event.followupTime = ZonedDateTime.now()
        event.sheetId = dummyEventSheet
        event.confirmAssignedEmailTemplateId = dummyEventSheet
        event.confirmCancelledEmailTemplateId = dummyEventSheet
        event.confirmAssignedEmailTemplateId = dummyEventSheet
        event.confirmRejectedEmailTemplateId = dummyEventSheet
        event.eventOrganizers = ["aianta03@gmail.com", "ianta@ualberta.ca"]
        event.volunteerCoordinators = ["aianta03@gmail.com", "ianta@ualberta.ca"]
        event.eventbriteLink = "eventbrite.ca"
        event.eventSlackChannel = "auto-wise"

        Shift s = new Shift()
        s.startTime = LocalTime.now()
        s.endTime = LocalTime.now()
        s.id = UUID.randomUUID()
        s.index = 1

        Role r = new Role()
        r.name = "General Volunteer"
        r.description = "Generally volunteer"
        r.shifts = [s]

        event.roles = [r]

        db.insert(event).onSuccess {
            testContext.completeNow()
        }.onFailure {
            testContext.failNow(it)
        }

        awaitContext(testContext)


    }

    private static void awaitContext(context){
        assertTrue(context.awaitCompletion(30, TimeUnit.SECONDS))
        if(context.failed()){
            throw context.causeOfFailure()
        }
    }
}
