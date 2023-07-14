package ca.ualberta.autowise

/**
 * @Author Alexandru Ianta
 * This verticle is responsible for bootstrapping all of AutoWiSE's functionality.
 */

//TICK = 600000 // 600,000 milliseconds, or 10 min ticks.
TICK = 1000

periodId = vertx.setPeriodic(TICK, id->{
    println "tick"
})

//void vertxStart(){
//
//
//
//}

//void vertxStop(){
//    vertx.cancelTimer(periodId)
//}