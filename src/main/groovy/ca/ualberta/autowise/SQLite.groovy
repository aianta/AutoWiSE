package ca.ualberta.autowise

import ca.ualberta.autowise.model.APICallContext
import ca.ualberta.autowise.model.HookType
import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.TaskStatus
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
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
import java.util.stream.Collectors


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
        CompositeFuture.all(
                createTasksTable(),
                createWebhooksTable(),
                createAPICallContextTable().compose {createAPICallTruncateTrigger()}
        ).onSuccess(done->{
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
                               advance_notify,
                               advance_notify_offset,
                               notify,
                               task_execution_time,
                               task_execution_time_epoch_milli,
                               status,
                               data
                               )
            VALUES (
                    ?,?,?,?,?,?,?,?,?,?
            );
            '''
            ).execute(Tuple.from([
                    task.taskId,
                    task.eventId,
                    task.name,
                    task.advanceNotify,
                    task.advanceNotifyOffset,
                    task.notify,
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
        .execute(Tuple.of(TaskStatus.COMPLETE, taskId.toString()))
        .onSuccess(rs->promise.complete())
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

    private Task taskFromRow(Row row){
        Task t = new Task(
                taskId: UUID.fromString(row.getString("task_id")),
                eventId: UUID.fromString(row.getString("event_id")),
                name: row.getString("name"),
                advanceNotify: row.getInteger("advance_notify") == 0?false:true,
                advanceNotifyOffset: row.getLong("advance_notify_offset"),
                notify: row.getInteger("notify") == 0?false:true,
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
            res->
                if(res){
                    log.info "Created webhooks table successfully"
                    promise.complete()
                }else{
                    log.error "Error creating webhooks table"
                    log.error res.cause().getMessage(), res.cause()
                    promise.fail(res.cause())
                }
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
            if (it){
                log.info "API Calls table created!"
                promise.complete()
            }else{
                log.error it.cause().getMessage(), it.cause()
                promise.fail(it.cause())
            }
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
                advance_notify INTEGER NOT NULL,
                advance_notify_offset INTEGER NOT NULL,
                notify INTEGER NOT NULL,
                task_execution_time TEXT NOT NULL,
                task_execution_time_epoch_milli INTEGER NOT NULL,
                status TEXT NOT NULL,
                data TEXT NOT NULL
            )
        ''')
        .execute({
            res->{
                if (res){
                    log.info "Tasks table created!"
                    promise.complete()
                }else{
                    log.error res.cause().getMessage(), res.cause()
                    promise.fail(res.cause())
                }
            }
        })

        return promise.future()
    }
}
