package ca.ualberta.autowise

import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * @Author Alexandru Ianta
 *
 * Provide script access to a local datastore.
 */
class SQLite {

    def log = LoggerFactory.getLogger(SQLite.class)
    static DB_CONNECTION_STRING = "jdbc:sqlite:autowise.db"


    static instance;

    static createInstance(dbString){
        DB_CONNECTION_STRING = dbString
        instance = new SQLite()
        return instance
    }

    static getInstance(){
        if(instance == null){
            throw RuntimeException("Cannot retrieve SQLite as it has not been created!")
        }
        return instance
    }

    def getConnection(){
        try(Connection conn = DriverManager.getConnection(DB_CONNECTION_STRING)){
            log.info "Connection established to database."
            return conn;

        }catch (SQLException e){
            log.error e.getMessage(), e
        }
    }

    private SQLite(){
        getConnection();
    }
}
