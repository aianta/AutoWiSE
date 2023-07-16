package ca.ualberta.autowise

import ca.ualberta.autowise.model.Task
import ca.ualberta.autowise.scripts.google.EventSlurper
import io.vertx.core.Promise
import io.vertx.core.CompositeFuture
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.jdbcclient.JDBCPool
import io.vertx.sqlclient.Tuple
import org.slf4j.LoggerFactory

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

    static createInstance(vertx, dbString){
        instance = new SQLite(vertx, dbString)
        return instance
    }

    static getInstance(){
        if(instance == null){
            throw RuntimeException("Cannot retrieve SQLite as it has not been created!")
        }
        return instance
    }


    private SQLite(vertx, dbString){
        this.vertx = vertx;

        JsonObject config = new JsonObject()
            .put("url", dbString)
            .put("max_pool_size", 16)

        pool = JDBCPool.pool(vertx, config);
        createTasksTable()
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

    def createTasksTable(){

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
                }else{
                    log.error res.cause().getMessage(), res.cause()
                }
            }
        })
    }
}
