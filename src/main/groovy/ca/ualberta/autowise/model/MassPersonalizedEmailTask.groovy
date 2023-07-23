package ca.ualberta.autowise.model

import io.vertx.core.Future
import io.vertx.core.Promise

interface MassPersonalizedEmailTask {

    Future contentResolution()

    Future assembleContent(String target, content)

    Future<Set<String>> targets()

    Future send(target, content, subject)

    Future confirmSentSuccessfully(target)

    Future postTaskBookkeeping()

}
