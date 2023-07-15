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
    def dbName = "jdbc:sqlite:autowise.db"


    static instance;

    static createInstance(){
        instance = new SQLite()
        return instance
    }

    static getInstance(){
        if(instance == null){
            createInstance()
        }
        return instance
    }

    def getConnection(){
        try(Connection conn = DriverManager.getConnection(dbName)){
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
