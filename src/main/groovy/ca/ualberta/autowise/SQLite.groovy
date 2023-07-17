package ca.ualberta.autowise

import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.model.TaskStatus
import ca.ualberta.autowise.model.Webhook
import ca.ualberta.autowise.scripts.google.EventSlurper
import groovy.transform.Field
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
                createWebhooksTable()
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
                               status
                               )
            VALUES (
                    ?,?,?,?,?,?,?,?,?
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
                    task.status.toString()
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

    /**
     * Returns all scheduled tasks that need to be done at the moment ordered by ascending
     * execution times.
     */
    def getWork(){
        def promise = Promise.promise()
        def currentTime = ZonedDateTime.now()
        log.info "getting work"
        pool.preparedQuery('''
            SELECT * FROM tasks WHERE status = ? and task_execution_time_epoch_milli <= ?
                ORDER BY task_execution_time_epoch_milli ASC
            ;
        ''')
        .execute(Tuple.of(TaskStatus.SCHEDULED.toString(), currentTime.toInstant().toEpochMilli()),
                {result->
                    if(result){
                        RowSet rows = result.result();
                        List<Task> tasks = new ArrayList<>()
                        for (Row row: rows){
                            tasks.add(taskFromRow(row))
                        }

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
                status: TaskStatus.valueOf(row.getString("status"))
        )
        return t
    }

    def insertWebhooks(List<Webhook> hooks){
        return CompositeFuture.all(hooks.stream()
                .map(h->insertWebhook(h))
                .collect(Collectors.toList()))
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
                status TEXT NOT NULL
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
