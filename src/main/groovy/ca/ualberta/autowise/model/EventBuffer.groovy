package ca.ualberta.autowise.model

/**
 * @Author Alexandru Ianta
 *
 * A list of partially valid/incomplete/complete events that {@link ca.ualberta.autowise.SlackBolt} keeps in memory.
 * This is used to persist events between campaign creation slack modals.
 *
 * It removes old events to prevent memory leaks.
 */
class EventBuffer extends ArrayList<Event>{

    private static final MAX_EVENTS = 25; //Number of events to keep in buffer before deleting old ones.

    def get(String id){
        return get(UUID.fromString(id))
    }

    def get(UUID id){
        return stream().filter {it.id.equals(id)}.findFirst().orElse(null)
    }

}
