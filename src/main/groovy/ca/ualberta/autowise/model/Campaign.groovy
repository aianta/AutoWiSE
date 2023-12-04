package ca.ualberta.autowise.model

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Campaign {

    private static final Logger log = LoggerFactory.getLogger(Campaign.class);

    UUID eventId;

    List<ContactStatus> contactStatuses;


}
