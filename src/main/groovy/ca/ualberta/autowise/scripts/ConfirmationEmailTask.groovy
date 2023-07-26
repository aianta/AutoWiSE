package ca.ualberta.autowise.scripts

import ca.ualberta.autowise.model.Task
import groovy.transform.Field
import io.vertx.rxjava3.core.Vertx
import org.slf4j.LoggerFactory

@Field static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.ConfirmationEmailTask.class)

static def confirmationEmailTask(Vertx vertx, services, Task task ){

}