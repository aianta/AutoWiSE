package ca.ualberta.autowise

import ca.ualberta.autowise.model.APICallContext
import ca.ualberta.autowise.model.ContactStatus
import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.ShiftAssignment
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.TaskStatus
import ca.ualberta.autowise.model.VolunteerConfirmation
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.CompositeFuture
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.jdbcclient.JDBCPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import org.slf4j.LoggerFactory

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.logging.Handler
import java.util.stream.Collectors

import static ca.ualberta.autowise.utils.JsonUtils.*;
/**
 * @Author Alexandru Ianta
 *
 * Provide script access to a local datastore.
 */
class SQLite {

    def log = LoggerFactory.getLogger(SQLite.class)

    Vertx vertx;
    JDBCPool pool;

    static instance;

    static createInstance(vertx, dbString, Future startup){
        instance = new SQLite(vertx, dbString, startup)
        return instance
    }

    static getInstance(){
        if(instance == null){
            throw RuntimeException("Cannot retrieve SQLite as it has not been created!")
        }
        return instance
    }


    private SQLite(vertx, dbString, Future startup){
        this.vertx = vertx;

        JsonObject config = new JsonObject()
            .put("url", dbString)
            .put("max_pool_size", 16)

        pool = JDBCPool.pool(vertx, config);
        CompositeFuture.all([
                createTasksTable(),
                createWebhooksTable(),
                createContactStatusTable(),
                createEventStatusTable(),
                createEventTable(),
                createVolunteerConfirmationTable(),
                createAPICallContextTable().compose {createAPICallTruncateTrigger()}
        ]).onSuccess(done->{
            startup.complete(this)
        })

    }

    def insertPlan(List<Task> tasks){
        log.info("Inserting tasks!")
        def future
        try{

            future = CompositeFuture.all(tasks.stream()
                    .map {t->insert(t)}
                    .collect(Collectors.toList())).onFailure(err->log.error err.getMessage(), err)
        }catch (Exception e){
            log.error e.getMessage(), e
        }

        return future
    }

    def insert(Task task){
        log.info "Attempting to insert task ${task.taskId.toString().substring(0,8)} - ${task.name} for event ${task.eventId.toString()}"
        Promise promise = Promise.promise()
        try{
            pool.preparedQuery('''
            INSERT INTO tasks (
                               task_id,
                               event_id,
                               name,
                               task_execution_time,
                               task_execution_time_epoch_milli,
                               status,
                               data
                               )
            VALUES (
                    ?,?,?,?,?,?,?
            );
            '''
            ).execute(Tuple.from([
                    task.taskId,
                    task.eventId,
                    task.name,
                    task.taskExecutionTime.format(EventSlurper.eventTimeFormatter),
                    task.taskExecutionTime.toInstant().toEpochMilli(),
                    task.status.toString(),
                    task.data.encode()
            ]), res->{
                if(res){
                    log.info "Successfully inserted task ${task.taskId.toString().substring(0,8)} - ${task.name} for event ${task.eventId.toString()}"
                    promise.complete(task)
                }else{
                    log.error res.cause().getMessage(), res.cause()
                    promise.fail(res.cause())
                }
            })
        }catch (Exception e){
            log.error e.getMessage(), e
        }

        return promise.future()
    }

    def markWebhookInvoked(webhookId){
        def promise = Promise.promise();
        pool.preparedQuery('''
            UPDATE webhooks 
            SET invoked = 1, 
                invoked_on = ?
            WHERE webhook_id = ?;
        ''')
        .execute(Tuple.of(ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter), webhookId.toString()))
        .onSuccess{
            promise.complete()
        }
        .onFailure{
            err-> log.error err.getMessage, err
                promise.fail(err)
        }

        return promise.future()

    }

    def cancelCampaign(eventId){
        def cancelTasks = Promise.promise()
        def cancelWebhooks = Promise.promise();
        pool.preparedQuery('''
            UPDATE webhooks 
            SET invoked = 1, invoked_on = ?
            WHERE event_id = ?;
        ''')
        .execute(Tuple.of(
                ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter),
                eventId.toString()
        )).onSuccess{
            cancelWebhooks.complete()
        }.onFailure{
            err->log.error err.getMessage(), err
                cancelWebhooks.fail(err)
        }

        pool.preparedQuery('''
            UPDATE tasks 
            SET status = ? 
            WHERE event_id = ?;
        ''')
        .execute(Tuple.of(
                TaskStatus.CANCELLED.toString(),
                eventId.toString()

        )).onSuccess{
            cancelTasks.complete()
        }.onFailure{
            err->log.error err.getMessage(), err
                cancelTasks.fail(err)
        }
        return CompositeFuture.all(cancelWebhooks.future(), cancelTasks.future())
    }

    def cancelTaskById(taskId){
        def promise = Promise.promise();
        pool.preparedQuery('''
            UPDATE tasks 
            SET status = ?
            WHERE task_id = ?;
        ''')
        .execute(Tuple.of(TaskStatus.CANCELLED.toString(), taskId.toString()))
        .onSuccess{
            rowSet->promise.complete()
        }.onFailure{
            err->log.error err.getMessage(), err
                promise.fail(err)
        }

        return promise.future();
    }

    def markTaskComplete(taskId){
        def promise = Promise.promise();
        pool.preparedQuery('''
            UPDATE tasks 
            SET status = ?
            WHERE task_id = ?;
        ''')
        .execute(Tuple.from([TaskStatus.COMPLETE, taskId.toString()]))
        .onSuccess{promise.complete()}
        .onFailure {err->log.error err.getMessage(), err}
        return promise.future()
    }

    def getWorkByTaskId(taskId){
        Promise<Task> promise = Promise.promise()
        pool.preparedQuery('''
            UPDATE tasks
            SET status = ?
            WHERE task_id = ? AND status = ?
            RETURNING *;
        ''')
        .execute(Tuple.of(
                TaskStatus.IN_PROGRESS,
                taskId.toString(),
                TaskStatus.SCHEDULED
        )).onSuccess {
            rowSet->
                if(rowSet.size() != 1){
                    promise.fail("Already executed or not found!")
                }else{
                    promise.complete(taskFromRow(rowSet.iterator().next()))
                }

        }
        return promise.future()
    }

    def fetchAllPendingTasksForEvent(eventId){
        Promise<List<Task>> promise = Promise.promise()
        pool.preparedQuery('''
            SELECT * FROM tasks WHERE event_id = ? AND status = ? ORDER BY task_execution_time_epoch_milli ASC;
        ''')
        .execute(Tuple.of(eventId.toString(), TaskStatus.PENDING))
        .onSuccess {rowSet->
            List<Task> result = new ArrayList<>();
            for(Row row: rowSet){
                Task t = taskFromRow(row)
                result.add(t)
            }
            promise.complete(result)
        }
        .onFailure {err->log.error err.getMessage(), err}
        return promise.future()
    }

    def beginCampaign(eventId){
        def promise = Promise.promise();
        pool.preparedQuery('''
            UPDATE tasks 
            SET status = ?
            WHERE status = ? and event_id = ?
        ''')
        .execute(Tuple.of(TaskStatus.SCHEDULED.toString(), TaskStatus.PENDING.toString(), eventId.toString()),{
            result->
                if(result){
                    promise.complete()
                }else{
                    log.error(result.cause().getMessage(), result.cause())
                    promise.fail(result.cause())
                }
        })

        return promise.future()
    }

    /**
     * Returns all scheduled tasks that need to be done at the moment ordered by ascending
     * execution times.
     *
     * Updates status to 'IN_PROGRESS' as tasks are fetched to avoid fetching them on the next tick before they are complete.
     */
    def getWork(){
        def promise = Promise.promise()
        def currentTime = ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone)
        pool.preparedQuery('''
            UPDATE tasks 
            SET status = ? 
            WHERE status = ? and task_execution_time_epoch_milli <= ?
            RETURNING *;
            
        ''')
        .execute(Tuple.of(TaskStatus.IN_PROGRESS.toString(), TaskStatus.SCHEDULED.toString(), currentTime.toInstant().toEpochMilli()),
                {result->
                    if(result){
                        RowSet rows = result.result();
                        List<Task> tasks = new ArrayList<>()
                        for (Row row: rows){
                            tasks.add(taskFromRow(row))
                        }
                        //Sort in ascending order, 'oldest' first.
                        tasks.sort(Comparator.comparingLong(task->((Task)task).taskExecutionTime.toInstant().toEpochMilli()))

                        promise.complete(tasks)
                    }else{
                        log.error result.cause().getMessage(), result.cause()
                        promise.fail(result.cause())
                    }
                }

        )

        return promise.future()
    }

    def isInitialRecruitmentComplete(UUID eventId){
        def promise = Promise.promise();

        pool.preparedQuery('''
            SELECT status FROM tasks WHERE name = "Initial Recruitment Email" AND event_id = ?;
        ''').execute(Tuple.from([eventId.toString()]), {
            if(it){
                promise.complete(it.result().iterator().next().getString("status").equals("COMPLETE"))
            }else{
                promise.fail(it.cause())
            }

        })

        return promise.future()
    }

    private Task taskFromRow(Row row){
        Task t = new Task(
                taskId: UUID.fromString(row.getString("task_id")),
                eventId: UUID.fromString(row.getString("event_id")),
                name: row.getString("name"),
                taskExecutionTime: ZonedDateTime.parse(row.getString("task_execution_time"), EventSlurper.eventTimeFormatter),
                status: TaskStatus.valueOf(row.getString("status")),
                data: new JsonObject(row.getString("data"))
        )
        return t
    }

    private Webhook webhookFromRow(Row row){
        Webhook w = new Webhook(
                id: UUID.fromString(row.getString("webhook_id")),
                eventId: UUID.fromString(row.getString("event_id")),
                type: HookType.valueOf(row.getString("hook_type")),
                data: new JsonObject(row.getString("data")),
                expiry: row.getLong("expiry"),
                invoked: row.getInteger("invoked") == 0?false:true,
                invokedOn: row.getString("invoked_on") != null?ZonedDateTime.parse(row.getString("invoked_on"), EventSlurper.eventTimeFormatter):null
        )
        return w
    }

    def insertWebhooks(List<Webhook> hooks){
        return CompositeFuture.all(hooks.stream()
                .map(h->insertWebhook(h))
                .collect(Collectors.toList()))
    }

    def getActiveWebhooks(){
        Promise promise = Promise.promise();
        pool.preparedQuery('''
            SELECT * FROM webhooks WHERE expiry >= ? AND invoked = 0;
        ''')
        .execute(Tuple.of(ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).toInstant().toEpochMilli()))
        .onSuccess(rowSet->{
            Set<Webhook> webhooks = new HashSet<>()
            for (Row row: rowSet){
                webhooks.add(webhookFromRow(row))
            }
            promise.complete(webhooks)
        })
        .onFailure {
            err-> log.error err.getMessage(), err
                promise.fail(err)
        }

        return promise.future()
    }

    /**
     * Fetches a webhook by id, but also marks it as invoked in the process.
     * @param id
     * @return
     */
    def invokeAndGetWebhookById(id){
        Promise promise = Promise.promise()
        pool.preparedQuery('''
            UPDATE webhooks 
            SET invoked = 1, 
                invoked_on = ?
            WHERE webhook_id = ? AND invoked = 0
            RETURNING *;
        ''')
        .execute(Tuple.of(ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter), id.toString()))
        .onSuccess(rowSet->{
            List<Webhook> result = new ArrayList<>()
            for(Row row: rowSet){
                Webhook hook = webhookFromRow(row)
                result.add(hook)
            }

            //If the rowset is of size 0, there was no uninvoked webhook with that id.
            if(result.size() == 0){
                promise.fail("Already invoked!")
                return
            }

            promise.complete(result)
        }).onFailure{
            err->
                log.error err.getMessage(), err
                promise.fail(err)
        }

        return promise.future()
    }

    def confirmVolunteerShiftRole(sheetId, UUID eventId, volunteerName, volunteerEmail, shiftRoleString){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            INSERT INTO confirmations (
                                       event_id,
                                       sheet_id,
                                       volunteer_email,
                                       volunteer_name,
                                       shift_role,
                                       timestamp
            ) VALUES (?,?,?,?,?,?);
        ''').execute(Tuple.from([
                eventId.toString(),
                sheetId,
                volunteerEmail,
                volunteerName,
                shiftRoleString,
                ZonedDateTime.now(ca.ualberta.autowise.AutoWiSE.timezone).format(EventSlurper.eventTimeFormatter)
        ]), {
            if(it){
                promise.complete()
            }else{
                log.error "Error recording volunteer confirmation [{}-{}] sheetId: {} eventId:{}", shiftRoleString, volunteerName, sheetId, eventId
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })

        return promise.future();
    }


    def getVolunteerConfirmations(String sheetId){
        Promise<List<VolunteerConfirmation>> promise = Promise.promise();

        pool.preparedQuery('''
            SELECT * FROM confirmations WHERE sheet_id = ?;
        ''').execute(Tuple.from([
                sheetId
        ]), {
            if(it){
                List<VolunteerConfirmation> confirmations = new ArrayList<>();
                it.result().forEach {confirmations.add(VolunteerConfirmation.fromRow(it))}

                promise.complete(confirmations)
            }else{
                log.error "Error getting volunteer confirmations for event sheet: {}", sheetId
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })


        return promise.future();

    }
    def saveAPICallContext(APICallContext context){
        try{
            Promise promise = Promise.promise();
            pool.preparedQuery('''
            INSERT INTO api_calls (
                                   call_id,
                                   service_type,
                                   attempt,
                                   timestamp_str, 
                                   timestamp, 
                                   sheet_id,
                                   doc_id,
                                   invoking_method,
                                   error,
                                   context
            ) VALUES (?,?,?,?,?,?,?,?,?,?);
        ''')
                    .execute(Tuple.from([
                            context.id.toString(),
                            context.serviceType(),
                            context.attempt(),
                            context.timestamp.format(APICallContext.timestampFormat), //Nice readable format
                            context.timestamp.toEpochSecond(ZoneOffset.UTC),
                            context.sheetId(),
                            context.docId(),
                            context.method(),
                            context.error(),
                            context.encodePrettily()
                    ]), {
                        if(it){
                            log.info "Saved API Call context ${context.id.toString()} - ${context.attempt()}"
                            promise.complete(context)
                        }else{
                            log.error "Error saving API Call context ${context.id.toString()} - ${context.attempt()}"
                            log.error it.cause().getMessage(), it.cause()
                            promise.fail(it.cause())
                        }
                    })
            return promise.future()
        }catch (Exception e){
            log.error e.getMessage(), e
        }

    }

    def insertWebhook(Webhook hook){
        Promise promise = Promise.promise()
        pool.preparedQuery('''
            INSERT INTO webhooks (
                                  webhook_id, 
                                  event_id,
                                  hook_type,
                                  data,
                                  expiry,
                                  invoked,
                                  invoked_on
            ) VALUES (?,?,?,?,?,?,?);
        ''')
        .execute(Tuple.from([
                hook.id.toString(),
                hook.eventId.toString(),
                hook.type.toString(),
                hook.data.encode(),
                hook.expiry,
                hook.invoked,
                hook.invokedOn
        ]), {
            res->
                if(res){
                    log.info "Successfully inserted webhook ${hook.id.toString()}"
                    promise.complete(hook)
                }else{
                    log.error "Error inserting webhook into database"
                    log.error res.cause().getMessage(), res.cause()
                    promise.fail(res.cause())
                }
        })


        return promise.future()
    }

    def createWebhooksTable(){
        Promise promise = Promise.promise();

        pool.query('''
                CREATE TABLE IF NOT EXISTS webhooks (
                    webhook_id TEXT PRIMARY KEY,
                    event_id TEXT NOT NULL,
                    hook_type TEXT NOT NULL,
                    data TEXT NOT NULL,
                    expiry INTEGER NOT NULL,
                    invoked INTEGER NOT NULL,
                    invoked_on TEXT
                )
        ''').execute({
            handleTableCreate(promise, it, "Webhooks")
        })

        return promise.future();
    }

    /**
     * TODO: Make the primary key a composite key of the APICallContext id and the attempt value.
     * @return
     */
    def createAPICallContextTable(){
        Promise promise = Promise.promise();
        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS api_calls (
                call_id TEXT NOT NULL,
                service_type TEXT NOT NULL,
                attempt INTEGER NOT NULL,
                timestamp_str TEXT,
                timestamp INTEGER NOT NULL,
                sheet_id TEXT,
                doc_id TEXT,
                invoking_method TEXT,
                error TEXT,
                context TEXT NOT NULL,
                PRIMARY KEY (call_id, attempt)
            )
        ''').execute({
            handleTableCreate(promise, it, "API Calls")
        })

        return promise.future()
    }

    /**
     * Creates a database trigger that deletes API calls which occurred over a month ago.
     * Should keep the database from becoming unruly.
     * @return
     */
    def createAPICallTruncateTrigger(){
        Promise promise = Promise.promise()

        pool.preparedQuery('''
            CREATE TRIGGER IF NOT EXISTS api_call_clean_up AFTER INSERT ON api_calls BEGIN
                DELETE FROM api_calls WHERE timestamp < unixepoch('now', '-1 month');
            END;
        ''')
        .execute({
            if(it){
                log.info("Trigger that culls api calls older than 1 month, created successfully.")
                promise.complete()
            }else{
                log.error "Failed to create trigger culling api calls older than 1 month!"
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })

        return promise.future()
    }

    def createTasksTable(){
        Promise promise = Promise.promise()

        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS tasks (
                task_id TEXT PRIMARY KEY,
                event_id TEXT NOT NULL, 
                name TEXT NOT NULL,
                task_execution_time TEXT NOT NULL,
                task_execution_time_epoch_milli INTEGER NOT NULL,
                status TEXT NOT NULL,
                data TEXT NOT NULL
            )
        ''')
        .execute({
            handleTableCreate(promise, it, "Tasks")
        })

        return promise.future()
    }

    def createContactStatusTable(){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS contact_status(
                event_id TEXT NOT NULL,
                sheet_id TEXT NOT NULL,
                volunteer_email TEXT NOT NULL,
                last_contacted TEXT,
                status TEXT NOT NULL,
                accepted_on TEXT,
                rejected_on TEXT,
                cancelled_on TEXT,
                waitlisted_on TEXT,
                desired_shift_role TEXT,
                PRIMARY KEY (event_id, volunteer_email)
            )
        ''').execute({
            handleTableCreate(promise, it, "Contact Status")
                })

        return promise.future();
    }

    def createVolunteerTable(){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS volunteers (
                id TEXT PRIMARY KEY,
                email TEXT NOT NULL,
                name TEXT NOT NULL
            )
        ''')
        .execute({
            handleTableCreate(promise, it, "Volunteer")
        })

        return promise.future();
    }




    def populateShiftRoles(UUID eventId, String sheetId, List<String> shiftRoles ){
        Promise promise = Promise.promise()

        //If shift roles for this event already exist, remove them this generally should only occur in debugging/development scenarios.
        pool.preparedQuery('''
            DELETE FROM event_status WHERE sheet_id = ?;
        ''').execute(Tuple.from(sheetId), {
            if(it){

                CompositeFuture.all(shiftRoles.stream()
                        .map(shiftRole->insertAvailableShiftRole(eventId, sheetId, shiftRole))
                        .collect(Collectors.toList()))
                        .onSuccess {promise.complete()}
                        .onFailure {promise.fail(it)}

            }else{
                log.error "Error clearing any existing shift roles under sheetId {}", sheetId
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })

        return promise.future()
    }

    def insertAvailableShiftRole(UUID eventId, String sheetId, String shiftRole){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            INSERT INTO event_status (event_id, sheet_id, shift_role) VALUES (?,?,?);
        ''').execute(Tuple.from([
                eventId.toString(),
                sheetId,
                shiftRole
        ]), {
            if(it){
                promise.complete()
            }else{
                log.error "Error creating assignable shift role for event {} sheetId {} shiftRole: {}", eventId.toString(), sheetId, shiftRole
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })

        return promise.future()
    }

    def assignShiftRole(sheetId, String shiftRole, volunteerEmail, volunteerName){
        return assignShiftRole(sheetId, shiftRole, null, volunteerEmail, null, volunteerName)
    }

/**
 * https://stackoverflow.com/questions/29071169/update-query-with-limit-cause-sqlite
 * Update the event status table to assign a volunteer to a particular shift role.
 *
 * @param sheetId the sheet id of the event in question.
 * @param shiftRole the shift role being assigned.
 * @param originalVolunteerEmail the original volunteer email assigned to this shift role if it has already been assigned.
 * @param newVolunteerEmail the new volunteer email assigned to this shift role.
 * @param originalVolunteerName the original volunteer name assigned to this shift role if it has already been assigned.
 * @param newVolunteerName the new volunteer name assigned to this shift role.
 * @return a promise containing a boolean which will be true if the assignment was made, or false if the there was no valid shift role to assign the volunteer to
 */
    def assignShiftRole(sheetId, String shiftRole, originalVolunteerEmail, newVolunteerEmail, originalVolunteerName, newVolunteerName){
        Promise<Boolean> promise = Promise.promise();

        //First check to see if we have an assignable shift role for the parameters given
        pool.preparedQuery("""
            SELECT rowid FROM event_status 
                WHERE shift_role = ? AND
                      ${originalVolunteerEmail == null?"volunteer_email IS NULL": "volunteer_email = ?"} AND
                      ${originalVolunteerName == null?"volunteer_name IS NULL":"volunteer_name = ?"} AND
                      sheet_id = ? 
            ORDER BY sheet_id LIMIT 1
        """).execute(Tuple.from(
                originalVolunteerName == null ? [
                        shiftRole,sheetId
                ]:[
                shiftRole,
                originalVolunteerEmail,
                originalVolunteerName,
                sheetId
        ]), {
            if (it){
                //If the result size is 1 then we found an assignable slot
                if(it.result().size() == 1) {
                    //Go ahead and assign it.
                    pool.preparedQuery("""
                        UPDATE  event_status SET volunteer_email = ?,
                                                 volunteer_name = ?
                        WHERE rowid IN (
                            SELECT rowid FROM event_status 
                                         WHERE shift_role = ? AND
                                              ${originalVolunteerEmail == null?"volunteer_email IS NULL": "volunteer_email = ?"} AND
                                              ${originalVolunteerName == null?"volunteer_name IS NULL":"volunteer_name = ?"} AND
                                               sheet_id = ? ORDER BY sheet_id LIMIT 1 )
                    """)
                            .execute(Tuple.from(
                                    originalVolunteerName == null?
                                            [
                                            newVolunteerEmail,
                                            newVolunteerName,
                                            shiftRole,
                                            sheetId
                                    ]:[
                                    newVolunteerEmail,
                                    newVolunteerName,
                                    shiftRole,
                                    originalVolunteerEmail,
                                    originalVolunteerName,
                                    sheetId
                            ]), {
                                if(it){
                                    promise.complete(true)
                                }else{
                                    log.error "Error assigning {}({}) to shiftrole {} for sheetId {}", newVolunteerName, newVolunteerEmail, shiftRole, sheetId
                                    log.error it.cause().getMessage(), it.cause()
                                    promise.fail(it.cause())
                                }
                            })
                }else{
                    //No assignable slot found for given parameters
                    promise.complete(false)
                }
            }else{
                log.error "Error checking for assignable {} for sheetId: {}", shiftRole, sheetId
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })




        return promise.future()
    }

    def findAvailableShiftRoles(sheetId){
        Promise<Set<String>> promise = Promise.promise();

        pool.preparedQuery('''
            SELECT * from event_status WHERE volunteer_email IS NULL AND sheet_id = ?;
        ''')
        .execute(Tuple.from([
                sheetId
        ]), {
            if (it){
                Set<String> unassignedShiftRoles = new HashSet<>();
                for(Row row: it.result()){
                    unassignedShiftRoles.add(row.getString("shift_role"))
                }
                promise.complete(unassignedShiftRoles)
            }else{
                log.error "Error looking up available shift roles for sheet id {}", sheetId
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })

        return promise.future();
    }

    /**
     * Return all the shift roles for a particular event using the event's sheetId
     * This includes filled and unfilled shift roles.
     *
     * Primary use of this method is to build the event status sheet for an event.
     * @param sheetId
     * @return a list of shiftRoles for the given event sheet.
     */
    def getAllShiftRoles(sheetId){
        Promise<List<ShiftAssignment>> promise = Promise.promise()

        pool.preparedQuery('''
            SELECT * FROM event_status WHERE sheet_id = ?;
        ''').execute(Tuple.from([sheetId]), {
            if(it){
                List<ShiftAssignment> result  = new ArrayList<>()
                it.result().forEach {result.add(shiftAssignmentFromRow(it))}

                promise.complete(result)
            }else{
                log.error "Error getting all shift roles for sheetId: {}", sheetId
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })

        return promise.future();
    }

    def createEventStatusTable(){
        Promise promise = Promise.promise();
        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS event_status (
                event_id TEXT NOT NULL,
                sheet_id TEXT NOT NULL, 
                shift_role TEXT NOT NULL,
                volunteer_email TEXT,
                volunteer_name TEXT    
            )
        ''').execute({
            handleTableCreate(promise, it, "Event Status")
        })
        return promise.future();
    }

    def createShiftAssignmentTable(){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS shift_assignments (
                event_id TEXT NOT NULL,
                shift_id TEXT NOT NULL,
                shift_number NUMERIC NOT NULL,
                volunteer_id TEXT NOT NULL
                PRIMARY KEY (event_id, shift_id,shift_number)
            )
        ''')
        .execute({
            handleTableCreate(promise, it, "Shift Assignment")
        })

        return promise.future();
    }

    def createRoleTable(){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS roles (
                id TEXT PRIMARY KEY,
                event_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL
            )
        ''')        .execute({
            handleTableCreate(promise, it, "Role")
        })

        return promise.future();
    }

    def createShiftTable(){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS shift (
                shift_id TEXT PRIMARY KEY,
                event_id TEXT NOT NULL,
                role_id TEXT NOT NULL,
                shift_index NUMERIC NOT NULL,
                num_volunteers NUMERIC NOT NULL,
                start_time NUMERIC NOT NULL,
                end_time NUMERIC NOT NULL
            )
        ''')
                .execute({
                    handleTableCreate(promise, it, "Shift")
                })

        return promise.future();
    }


    def getUpcomingEvents(){
        Promise<List<Event>> promise = Promise.promise();

        pool.preparedQuery('''
            SELECT data FROM events WHERE start_time >= ?; 
        ''').execute(Tuple.from([
                ZonedDateTime.now().toInstant().toEpochMilli()
        ]), {
            if(it){
                List<Event> results = new ArrayList<>();

                it.result().forEach {results.add(slurpEventJson(it.getString("data")))}

                promise.complete(results)
            }else{
                log.error "Error fetching upcoming events!"
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })

        return promise.future()
    }

    def updateEventStatus(UUID eventId, EventStatus status){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            SELECT data FROM events WHERE id = ?;
        ''').execute(Tuple.from([eventId.toString()]), {
            if(it){
                Event event = slurpEventJson(it.result().iterator().next().getString("data"));
                event.status = status.toString();

                pool.preparedQuery('''
                    UPDATE events 
                    SET
                        data = ?,
                        status = ?
                    WHERE
                        id = ?;
                ''').execute(Tuple.from([
                        getEventGenerator().toJson(event), status.toString(), eventId.toString()
                        ]), {
                            if(it){
                                promise.complete()
                            }else{
                                log.error "Error updating event status"
                                log.error it.cause().getMessage(), it.cause()
                                promise.fail(it.cause())
                            }
                        })
            }
        })

        return promise.future();
    }

    def getEvent(UUID eventId){
        Promise<Event> promise = Promise.promise();

        pool.preparedQuery('''
            SELECT data FROM events WHERE id = ?;
        ''').execute(Tuple.from([eventId.toString()]),
                {
                    if(it){
                        Row eventRow = it.result().iterator().next()
                        promise.complete(slurpEventJson(eventRow.getString("data")))
                    }else{
                        log.error "Error getting event {} from database", eventId.toString()
                        log.error it.cause().getMessage(), it.cause()
                        promise.fail(it.cause())
                    }
                }
        )

        return promise.future()
    }

    def insert(Event event){
        Promise<Event> promise = Promise.promise();

        pool.preparedQuery('''
            INSERT INTO events (id, status,spreadsheet_id, name, start_time, end_time, data, weblink) VALUES (?,?,?,?,?,?,?,?);
        ''').execute(Tuple.from([
                event.id.toString(),
                event.status,
                event.sheetId,
                event.name,
                event.startTime.toInstant().toEpochMilli(),
                event.endTime.toInstant().toEpochMilli(),
                getEventGenerator().toJson(event),
                event.weblink
        ]), {
            if(it){
                promise.complete(event)
            }else{
                log.error "Error inserting new event into database"
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
        })


        return promise.future();
    }

    def createEventTable(){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS events (
                id TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                spreadsheet_id TEXT,
                weblink TEXT, 
                name TEXT NOT NULL,
                start_time NUMERIC NOT NULL,
                end_time NUMERIC NOT NULL,
                data TEXT NOT NULL
            )
        ''').execute({
            handleTableCreate(promise, it, "Event")
        })
        return promise.future();
    }

    def createVolunteerConfirmationTable(){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS confirmations (
                event_id TEXT NOT NULL,
                sheet_id TEXT NOT NULL,
                volunteer_email TEXT NOT NULL,
                volunteer_name TEXT NOT NULL,
                shift_role TEXT NOT NULL,
                timestamp TEXT,
                PRIMARY KEY (event_id, volunteer_email)
                                                
            )
        ''').execute({
            handleTableCreate(promise, it, "Volunteer Confirmations")
        })

        return promise.future();

    }

    def createShiftRoleTable(){
        Promise promise = Promise.promise();

        pool.preparedQuery('''
            CREATE TABLE IF NOT EXISTS shift_roles (
                shift_role TEXT NOT NULL,
                volunteer_email TEXT NOT NULL,
                volunteer_name TEXT NOT NULL,
                event_id TEXT NOT NULL,
                sheet_id TEXT NOT NULL
            )
        ''')
        .execute({ handleTableCreate(promise, it, "Shift Role Table")})

        return promise.future();
    }




    private void handleTableCreate(Promise promise, AsyncResult<RowSet<Row>> result, String tableName){
        if(result){
            log.info "${tableName} table created!"
            promise.complete()
        }else{
            log.error result.cause().getMessage(), result.cause()
            promise.fail(result.cause())
        }
    }




    def updateVolunteerContactStatus(ContactStatus contactStatus){
        Promise promise = Promise.promise()

        pool.preparedQuery('''
            UPDATE contact_status 
            SET 
                last_contacted = ?,
                status = ?,
                accepted_on = ?,
                rejected_on = ?,
                cancelled_on = ?,
                waitlisted_on = ?,
                desired_shift_role = ?
            WHERE
                event_id = ? AND sheet_id = ? AND volunteer_email = ?
        ''')
        .execute(Tuple.of(
                contactStatus.lastContacted != null?contactStatus.lastContacted.format(EventSlurper.eventTimeFormatter):null,
                contactStatus.status,
                contactStatus.acceptedOn != null?contactStatus.acceptedOn.format(EventSlurper.eventTimeFormatter):null,
                contactStatus.rejectedOn != null?contactStatus.rejectedOn.format(EventSlurper.eventTimeFormatter):null,
                contactStatus.cancelledOn != null?contactStatus.cancelledOn.format(EventSlurper.eventTimeFormatter):null,
                contactStatus.waitlistedOn != null?contactStatus.waitlistedOn.format(EventSlurper.eventTimeFormatter):null,
                contactStatus.desiredShiftRole,
                contactStatus.eventId.toString(),
                contactStatus.sheetId,
                contactStatus.volunteerEmail
        ))
        .onSuccess {promise.complete()}
        .onFailure { log.error it.getMessage(), it
                promise.fail(it)
        }

        return promise.future();

    }

    def getEventContactStatusTable(UUID eventId){
        Promise<List<ContactStatus>> promise = Promise.promise();

        pool.preparedQuery('''
            SELECT * FROM contact_status WHERE event_id = ?;
        ''')
        .execute(Tuple.of(eventId.toString()))
        .onSuccess {

            List<ContactStatus> contactStatusTable = new ArrayList<>();
            Iterator<Row> iterator = it.iterator();
            while (iterator.hasNext()){
                Row row = iterator.next();
                contactStatusTable.add(contactStatusFromRow(row))
            }
            promise.complete(contactStatusTable)

        }
        .onFailure {log.error it.getMessage(), it
            promise.fail(it)
        }

        return promise.future();
    }

    def insertContactStatus(ContactStatus contactStatus){

        log.info "Inserting contact status for {}", contactStatus.volunteerEmail

        Promise promise = Promise.promise();

        pool.preparedQuery('''
        INSERT INTO contact_status (
            event_id,
            sheet_id,
            volunteer_email,
            last_contacted, 
            status,
            accepted_on,
            rejected_on,
            cancelled_on,
            waitlisted_on,
            desired_shift_role                                    
        ) VALUES (?,?,?,?,?,?,?,?,?,?)
        ''')
        .execute(Tuple.of(
                contactStatus.eventId.toString(),
                contactStatus.sheetId,
                contactStatus.volunteerEmail,
                contactStatus.lastContacted != null?contactStatus.lastContacted.format(EventSlurper.eventTimeFormatter):null,
                contactStatus.status,
                contactStatus.acceptedOn != null? contactStatus.acceptedOn.format(EventSlurper.eventTimeFormatter):null,
                contactStatus.rejectedOn != null? contactStatus.rejectedOn.format(EventSlurper.eventTimeFormatter):null,
                contactStatus.cancelledOn != null? contactStatus.cancelledOn.format(EventSlurper.eventTimeFormatter): null,
                contactStatus.waitlistedOn !=null? contactStatus.waitlistedOn.format(EventSlurper.eventTimeFormatter): null,
                contactStatus.desiredShiftRole
        ))
        .onSuccess {promise.complete()}
        .onFailure {log.error it.getMessage(), it
            promise.fail(it)
        }

        return promise.future();
    }


    def getVolunteerContactStatus(UUID eventId, String volunteerEmail){
        Promise<ContactStatus> promise = Promise.promise();


        pool.preparedQuery('''
            SELECT * FROM contact_status WHERE event_id = ? AND volunteer_email = ?;
        ''').execute(Tuple.of(eventId.toString(), volunteerEmail))
        .onSuccess {rowSet->{
            if(rowSet.size() == 0){
                promise.fail("Could not find contact status for event ${eventId.toString()} and volunteer ${volunteerEmail} ")
            }
            Row row = rowSet.iterator().next();
            promise.complete(contactStatusFromRow(row))
        }}

        return promise.future();

    }

    ShiftAssignment shiftAssignmentFromRow(Row row){
        ShiftAssignment result = new ShiftAssignment();
        result.eventId = UUID.fromString(row.getString("event_id"));
        result.sheetId = row.getString("sheet_id");
        result.volunteerEmail = row.getString("volunteer_email") == null?null:row.getString("volunteer_email");
        result.volunteerName = row.getString("volunteer_name") == null?null:row.getString("volunteer_name");
        result.shiftRole = row.getString("shift_role")
        return  result;
    }

    ContactStatus contactStatusFromRow(Row row){
        ContactStatus contactStatus = new ContactStatus()
        contactStatus.eventId = UUID.fromString(row.getString("event_id"));
        contactStatus.sheetId = row.getString("sheet_id");
        contactStatus.volunteerEmail = row.getString("volunteer_email");
        contactStatus.lastContacted = row.getString("last_contacted") != null?ZonedDateTime.parse(row.getString("last_contacted"), EventSlurper.eventTimeFormatter):null;
        contactStatus.status = row.getString("status")
        contactStatus.acceptedOn = row.getString("accepted_on") != null?ZonedDateTime.parse(row.getString("accepted_on"), EventSlurper.eventTimeFormatter): null;
        contactStatus.rejectedOn = row.getString("rejected_on") != null?ZonedDateTime.parse(row.getString("rejected_on"), EventSlurper.eventTimeFormatter): null;
        contactStatus.cancelledOn = row.getString("cancelled_on") != null?ZonedDateTime.parse(row.getString("cancelled_on"), EventSlurper.eventTimeFormatter): null;
        contactStatus.waitlistedOn = row.getString("waitlisted_on") != null?ZonedDateTime.parse(row.getString("waitlisted_on"), EventSlurper.eventTimeFormatter): null;
        contactStatus.desiredShiftRole = row.getString("desired_shift_role")
        return contactStatus;
    }


}
