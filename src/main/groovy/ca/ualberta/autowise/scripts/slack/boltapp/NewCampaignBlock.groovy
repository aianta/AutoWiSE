package ca.ualberta.autowise.scripts.slack.boltapp

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory

import java.time.Instant

class NewCampaignBlock {

    static def log = LoggerFactory.getLogger(ca.ualberta.autowise.scripts.slack.boltapp.NewCampaignBlock.class)

    public static final String DEFAULT_INITIAL_RECRUITMENT_EMAIL_TEMPLATE_ID = "1rKlMxnlmWEMkAKmWWnXabtbU9KfHqKZ7pHJeTF41zMg"
    public static final String DEFAULT_RECRUITMENT_EMAIL_TEMPLATE_ID = "1rKlMxnlmWEMkAKmWWnXabtbU9KfHqKZ7pHJeTF41zMg"
    public static final String DEFAULT_FOLLOWUP_EMAIL_TEMPLATE_ID = "1gOfAGD_DSSXRntafyGvfDO4BXo9D1eYDVYYZDaDVk-I"
    public static final String DEFAULT_CONFIRM_ASSIGNED_EMAIL_TEMPLATE_ID = "10s4KAx-EVPWfRsKURfItMgoeWcShrdDO0aduEyxdnG4"
    public static final String DEFAULT_CONFIRM_REJECTED_EMAIL_TEMPLATE_ID = "1LPb9DQSYa4r-6YVRHuqLS_ckGNZHoG-qnDywjgPsQzI"
    public static final String DEFAULT_CONFIRM_WAITLIST_EMAIL_TEMPLATE_ID = "15kwGYPpDYKBgKoQ-x4NojurMbCADFuDaOGttv3WLbAs"
    public static final String DEFAULT_CONFIRM_CANCELLED_EMAIL_TEMPLATE_ID = "1DP63CwPtKB98yf4MKhPjCp_EQnxDgQgIVpZkxnNSYZ0"

    /**
     * Define block and action ids in the modal UI
     */
    public static final String EVENT_NAME_BLOCK = "event_name_block"
    public static final String EVENT_NAME_ACTION = "event_name"
    public static final String EVENT_DESCRIPTION_BLOCK = "event_description_block"
    public static final String EVENT_DESCRIPTION_ACTION = "event_description"
    public static final String EVENT_ORGANIZERS_BLOCK = "event_organizers_block"
    public static final String EVENT_ORGANIZERS_ACTION = "event_organizers"
    public static final String EVENT_VOLUNTEER_COORDINATORS_BLOCK = "volunteer_coordinators_block"
    public static final String EVENT_VOLUNTEER_COORDINATORS_ACTION = "volunteer_coordinators"
    public static final String EVENT_START_BLOCK = "event_start_block"
    public static final String EVENT_START_ACTION = "event_start"
    public static final String EVENT_END_BLOCK = "event_end_block"
    public static final String EVENT_END_ACTION = "event_end"
    public static final String EVENT_EVENTBRITE_BLOCK = "eventbrite_block"
    public static final String EVENT_EVENTBRITE_ACTION = "eventbrite_link"
    public static final String EVENT_SLACK_CHANNEL_BLOCK = "event_slack_channel_block"
    public static final String EVENT_SLACK_CHANNEL_ACTION = "event_channel"
    public static final String EVENT_CAMPAIGN_START_BLOCK = "campaign_start_block"
    public static final String EVENT_CAMPAIGN_START_ACTION = "campaign_start_datetime"
    public static final String EVENT_RESOLICIT_FREQUENCY_BLOCK = "resolicit_frequency_block"
    public static final String EVENT_RESOLICIT_FREQUENCY_ACTION = "resolicit_frequency"
    public static final String EVENT_FOLLOWUP_BLOCK = "followup_datetime_block"
    public static final String EVENT_FOLLOWUP_ACTION = "followup_datetime"
    public static final String EVENT_INITIAL_RECRUITMENT_EMAIL_TEMPLATE_BLOCK = "initial_recruitment_email_template_block"
    public static final String EVENT_INITIAL_RECURITMENT_EMAIL_TEMPLATE_ACTION = "initial_recruitment_email_template"
    public static final String EVENT_RECRUITMENT_EMAIL_TEMPLATE_BLOCK = "recruitment_email_template_block"
    public static final String EVENT_RECRUITMENT_EMAIL_TEMPLATE_ACTION = "recruitment_email_template"
    public static final String EVENT_FOLLOWUP_EMAIL_TEMPLATE_BLOCK = "followup_email_template_block"
    public static final String EVENT_FOLLOWUP_EMAIL_TEMPLATE_ACTION = "followup_email_template"
    public static final String EVENT_CONFIRM_ASSIGNED_EMAIL_TEMPLATE_BLOCK = "confirm_assigned_email_template_block"
    public static final String EVENT_CONFIRM_ASSIGNED_EMAIL_TEMPLATE_ACTION = "confirm_assigned_email_template"
    public static final String EVENT_CONFIRM_CANCELLED_EMAIL_TEMPLATE_BLOCK = "confirm_cancelled_email_template_block"
    public static final String EVENT_CONFIRM_CANCELLED_EMAIL_TEMPLATE_ACTION = "confirm_cancelled_email_template"
    public static final String EVENT_CONFIRM_WAITLIST_EMAIL_TEMPLATE_BLOCK = "confirm_waitlist_email_template_block"
    public static final String EVENT_CONFIRM_WAITLIST_EMAIL_TEMPLATE_ACTION = "confirm_waitlist_email_template"
    public static final String EVENT_CONFIRM_REJECTED_EMAIL_TEMPLATE_BLOCK = "confirm_rejected_email_template_block"
    public static final String EVENT_CONFIRM_REJECTED_EMAIL_TEMPLATE_ACTION = "confirm_rejected_email_template"
    public static final String EVENT_NUMBER_OF_ROLES_BLOCK = "number_of_roles_block"
    public static final String EVENT_NUMBER_OF_ROLES_ACTION = "num_roles"

    private static def getTemplateOptionString(id, JsonArray options){
        log.info "Looking for id: ${id} in \n${options}"
        return options.stream().filter {it.getString("value").equals(id)}.findFirst().orElse(null).encodePrettily()
    }

      static def viewString(Set<Pair<String,String>> templateOptions, List<String> channelOptions){
          log.info("Creating new campaign view with ${templateOptions} template options")

          def channelOptionsJson = new JsonArray()
          channelOptions.forEach{channel->
              channelOptionsJson.add(new JsonObject()
                .put("text", new JsonObject()
                    .put("type", "plain_text")
                        .put("text", channel)
                ).put("value", channel)
              )
          }

          def templateOptionsJson = new JsonArray()
          templateOptions.forEach{pair->
              templateOptionsJson.add(new JsonObject()
                      .put("text", new JsonObject()
                            .put("type", "plain_text")
                            .put("text", pair.getValue()))
                      .put("value", pair.getKey()))
          }

          log.info("template options: ${templateOptionsJson.encodePrettily()}")

          def templateOptionsString = templateOptionsJson.encodePrettily()

          def currentDateEpochSeconds = Instant.now().getEpochSecond()

        return """
{
	"type": "modal",
	"callback_id":"create_new_campaign",
	"title": {
		"type": "plain_text",
		"text": "AutoWiSE",
		"emoji": true
	},
	"submit": {
		"type": "plain_text",
		"text": "Submit",
		"emoji": true
	},
	"close": {
		"type": "plain_text",
		"text": "Cancel",
		"emoji": true
	},
	"blocks": [
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Let's create a new volunteer recruitment campaign!\n\nAll date and time values will be in MST."
			}
		},
		{
			"type": "divider"
		},
		{
			"type": "input",
			"block_id": "${EVENT_NAME_BLOCK}",
			"element": {
				"type": "plain_text_input",
				"action_id": "${EVENT_NAME_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Event Name",
				"emoji": false
			}
		},
		{
			"type": "divider"
		},
		{
			"type": "input",
			"block_id": "${EVENT_DESCRIPTION_BLOCK}",
			"element": {
				"type": "plain_text_input",
				"multiline": true,
				"action_id": "${EVENT_DESCRIPTION_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Description",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_ORGANIZERS_BLOCK}",
			"element": {
				"type": "multi_users_select",
				"placeholder": {
					"type": "plain_text",
					"text": "Select users",
					"emoji": true
				},
				"action_id": "${EVENT_ORGANIZERS_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Event Organizers",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_VOLUNTEER_COORDINATORS_BLOCK}",
			"element": {
				"type": "multi_users_select",
				"placeholder": {
					"type": "plain_text",
					"text": "Select users",
					"emoji": true
				},
				"action_id": "${EVENT_VOLUNTEER_COORDINATORS_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Volunteer Coordinators",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id":"${EVENT_START_BLOCK}",
			"element": {
				"type": "datetimepicker",
				"initial_date_time": ${currentDateEpochSeconds},
				"action_id": "${EVENT_START_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Start Time",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_END_BLOCK}",
			"element": {
				"type": "datetimepicker",
				"initial_date_time": ${currentDateEpochSeconds},
				"action_id": "${EVENT_END_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "End Time",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id":"${EVENT_EVENTBRITE_BLOCK}",
			"element": {
				"type": "url_text_input",
				"action_id": "${EVENT_EVENTBRITE_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Eventbrite Link",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_SLACK_CHANNEL_BLOCK}",
			"label": {
				"type": "plain_text",
				"text": "Event Slack channel:"
			},
			"element": {
				"action_id": "${EVENT_SLACK_CHANNEL_ACTION}",
				"type": "static_select",
				"placeholder": {
					"type": "plain_text",
					"text": "Select a channel"
				},
				options: ${channelOptionsJson.encodePrettily()}
			}
		},
		{
			"type": "divider"
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "*Campaign Settings:* "
			}
		},
		{
			"type": "divider"
		},
		{
			"type": "input",
			"block_id":"${EVENT_CAMPAIGN_START_BLOCK}",
			"element": {
				"type": "datetimepicker",
				"initial_date_time": ${currentDateEpochSeconds},
				"action_id": "${EVENT_CAMPAIGN_START_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Start Volunteer Recruitment Campaign on:",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id":"${EVENT_RESOLICIT_FREQUENCY_BLOCK}",
			"element": {
				"type": "number_input",
				"is_decimal_allowed": false,
				"action_id": "${EVENT_RESOLICIT_FREQUENCY_ACTION}",
				"min_value": "1",
				"max_value": "90",
				"placeholder": {
					"type": "plain_text",
					"text": "X"
				}
			},
			"label": {
				"type": "plain_text",
				"text": "Send periodic reminder recruitment emails every X days:",
				"emoji": true
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "These follow up emails will not be sent to volunteers that indicated they are unavailable/uninterested, or volunteers that have already signed up for shifts."
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_FOLLOWUP_BLOCK}",
			"element": {
				"type": "datetimepicker",
				"initial_date_time": ${currentDateEpochSeconds},
				"action_id": "${EVENT_FOLLOWUP_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Follow-up with registered volunteers on:",
				"emoji": true
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Choose email templates from the docs available in the AutoWise google drive folder."
			}
		},
		{
			"type": "divider"
		},
		{
			"type": "input",
			"block_id": "${EVENT_INITIAL_RECRUITMENT_EMAIL_TEMPLATE_BLOCK}",
			"element": {
				"type": "static_select",
				"placeholder": {
				    "type": "plain_text",
				    "text": "Select a template..."
				},
				"options": ${templateOptionsString},
                "initial_option": ${getTemplateOptionString(DEFAULT_INITIAL_RECRUITMENT_EMAIL_TEMPLATE_ID, templateOptionsJson)},
				"action_id": "${EVENT_INITIAL_RECURITMENT_EMAIL_TEMPLATE_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Initial Recruitment Email Template",
				"emoji": false
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "The first recruitment email for this campaign."
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_RECRUITMENT_EMAIL_TEMPLATE_BLOCK}",
			"element": {
				"type": "static_select",
				"placeholder": {
				    "type":"plain_text",
				    "text": "Select a template..."
				},
				"options": ${templateOptionsString},
                "initial_option": ${getTemplateOptionString(DEFAULT_RECRUITMENT_EMAIL_TEMPLATE_ID, templateOptionsJson)},
				"action_id": "${EVENT_RECRUITMENT_EMAIL_TEMPLATE_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Recruitment Email Template",
				"emoji": false
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Subsequent recruitment emails for this campaign."
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_FOLLOWUP_EMAIL_TEMPLATE_BLOCK}",
			"element": {
				"type": "static_select",
				"placeholder": {
				    "type":"plain_text",
				    "text": "Select a template..."
				},
				"options": ${templateOptionsString},
                "initial_option": ${getTemplateOptionString(DEFAULT_FOLLOWUP_EMAIL_TEMPLATE_ID, templateOptionsJson)},
				"action_id": "${EVENT_FOLLOWUP_EMAIL_TEMPLATE_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Follow-up Email Template",
				"emoji": false
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Sent to volunteers who've signed up for shift/roles asking them to confirm they're still available."
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_CONFIRM_ASSIGNED_EMAIL_TEMPLATE_BLOCK}",
			"element": {
				"type": "static_select",
				"placeholder": {
				    "type":"plain_text",
				    "text": "Select a template..."
				},
				"options": ${templateOptionsString},
                "initial_option": ${getTemplateOptionString(DEFAULT_CONFIRM_ASSIGNED_EMAIL_TEMPLATE_ID, templateOptionsJson)},
				"action_id": "${EVENT_CONFIRM_ASSIGNED_EMAIL_TEMPLATE_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Confirm Assigned Email Template",
				"emoji": false
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Sent back to a volunteer who signs up for a shift/role for this event."
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_CONFIRM_CANCELLED_EMAIL_TEMPLATE_BLOCK}",
			"element": {
				"type": "static_select",
				"placeholder": {
				    "type":"plain_text",
				    "text": "Select a template..."
				},
				"options": ${templateOptionsString},
                "initial_option": ${getTemplateOptionString(DEFAULT_CONFIRM_CANCELLED_EMAIL_TEMPLATE_ID, templateOptionsJson)},
				"action_id": "${EVENT_CONFIRM_CANCELLED_EMAIL_TEMPLATE_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Confirm Cancelled Email Template",
				"emoji": false
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Sent back to a volunteer who cancels on a shift/role they have signed up for."
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_CONFIRM_WAITLIST_EMAIL_TEMPLATE_BLOCK}",
			"element": {
				"type": "static_select",
				"placeholder": {
				    "type":"plain_text",
				    "text": "Select a template..."
				},
				"options": ${templateOptionsString},
                "initial_option": ${getTemplateOptionString(DEFAULT_CONFIRM_WAITLIST_EMAIL_TEMPLATE_ID, templateOptionsJson)},
				"action_id": "${EVENT_CONFIRM_WAITLIST_EMAIL_TEMPLATE_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Confirm Waitlist Email Template",
				"emoji": false
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Sent back to a volunteer who signs-up for a shift/role that has already been fully staffed."
			}
		},
		{
			"type": "input",
			"block_id": "${EVENT_CONFIRM_REJECTED_EMAIL_TEMPLATE_BLOCK}",
			"element": {
				"type": "static_select",
				"placeholder": {
				    "type":"plain_text",
				    "text": "Select a template..."
				},
				"options": ${templateOptionsString},
                "initial_option": ${getTemplateOptionString(DEFAULT_CONFIRM_REJECTED_EMAIL_TEMPLATE_ID, templateOptionsJson)},
				"action_id": "${EVENT_CONFIRM_REJECTED_EMAIL_TEMPLATE_ACTION}"
			},
			"label": {
				"type": "plain_text",
				"text": "Confirm Rejected Email Template",
				"emoji": false
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Sent back to a volunteer who rejects the volunteer opportunities for this event."
			}
		},
		{
			"type": "divider"
		},
		{
			"type": "input",
			"block_id":"${EVENT_NUMBER_OF_ROLES_BLOCK}",
			"element": {
				"type": "number_input",
				"is_decimal_allowed": false,
				"action_id": "${EVENT_NUMBER_OF_ROLES_ACTION}",
				"min_value": "1",
				"max_value": "12",
				"placeholder": {
					"type": "plain_text",
					"text": "# of roles"
				}
			},
			"label": {
				"type": "plain_text",
				"text": "Number of volunteer roles:",
				"emoji": true
			}
		},
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": "Be very sure about this number of roles, you'll have to start over and fill this form again if it's incorrect.\n\nIf you click submit and nothing happens, scroll back up and check for errors."
			}
		}
	]
}       """.toString()
    }

}
