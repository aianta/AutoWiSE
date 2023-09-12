package ca.ualberta.autowise.scripts.slack.boltapp

class NewCampaignBlock {

    //TODO - extract block_ids and action_ids into static variables so that they can be less prone to errors when used by Slack Bolt.
    static def viewString(){
        return '''
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
			"block_id": "event_name_block",
			"element": {
				"type": "plain_text_input",
				"action_id": "event_name"
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
			"block_id": "event_description_block",
			"element": {
				"type": "plain_text_input",
				"multiline": true,
				"action_id": "event_description"
			},
			"label": {
				"type": "plain_text",
				"text": "Description",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id": "event_organizers_block",
			"element": {
				"type": "multi_users_select",
				"placeholder": {
					"type": "plain_text",
					"text": "Select users",
					"emoji": true
				},
				"action_id": "event_organizers"
			},
			"label": {
				"type": "plain_text",
				"text": "Event Organizers",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id": "volunteer_coordinators_block",
			"element": {
				"type": "multi_users_select",
				"placeholder": {
					"type": "plain_text",
					"text": "Select users",
					"emoji": true
				},
				"action_id": "volunteer_coordinators"
			},
			"label": {
				"type": "plain_text",
				"text": "Volunteer Coordinators",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id":"event_start_block",
			"element": {
				"type": "datetimepicker",
				"initial_date_time": 1628633820,
				"action_id": "event_start"
			},
			"label": {
				"type": "plain_text",
				"text": "Start Time",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id": "event_end_block",
			"element": {
				"type": "datetimepicker",
				"initial_date_time": 1628633820,
				"action_id": "event_end"
			},
			"label": {
				"type": "plain_text",
				"text": "End Time",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id":"eventbrite_block",
			"element": {
				"type": "url_text_input",
				"action_id": "eventbrite_link"
			},
			"label": {
				"type": "plain_text",
				"text": "Eventbrite Link",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id": "event_slack_channel_block",
			"label": {
				"type": "plain_text",
				"text": "Event Slack channel:"
			},
			"element": {
				"action_id": "event_channel",
				"type": "channels_select",
				"placeholder": {
					"type": "plain_text",
					"text": "Select a channel"
				}
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
			"block_id":"campaign_start_block",
			"element": {
				"type": "datetimepicker",
				"initial_date_time": 1628633820,
				"action_id": "campaign_start_datetime"
			},
			"label": {
				"type": "plain_text",
				"text": "Start Volunteer Recruitment Campaign on:",
				"emoji": true
			}
		},
		{
			"type": "input",
			"block_id":"resolicit_frequency_block",
			"element": {
				"type": "number_input",
				"is_decimal_allowed": false,
				"action_id": "resolicit_frequency",
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
			"block_id": "followup_datetime_block",
			"element": {
				"type": "datetimepicker",
				"initial_date_time": 1628633820,
				"action_id": "followup_datetime"
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
				"text": "*Email Templates:* \nThese values need to be google doc ids. You can find the id of a google doc by navigating to it in google drive and then checking the address bar at the top of your browser window.\n\n It will look something like this: \n`<http://about:blank|https://docs.google.com/document/d/ *1gOfAGD_DSSXRntafyGvfDO4BXo9D1eYDVYYZDaDVk-I* /edit>`\n The section in bold is the value you need to enter in the fields below. \n\n Please note, for this to work, the provided google docs must be shared with the google account used by autowise. "
			}
		},
		{
			"type": "divider"
		},
		{
			"type": "input",
			"block_id": "initial_recruitment_email_template_block",
			"element": {
				"type": "plain_text_input",
				"action_id": "initial_recruitment_email_template"
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
			"block_id": "recruitment_email_template_block",
			"element": {
				"type": "plain_text_input",
				"action_id": "recruitment_email_template"
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
			"block_id": "followup_email_template_block",
			"element": {
				"type": "plain_text_input",
				"action_id": "followup_email_template"
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
			"block_id": "confirm_assigned_email_template_block",
			"element": {
				"type": "plain_text_input",
				"action_id": "confirm_assigned_email_template"
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
			"block_id": "confirm_cancelled_email_template_block",
			"element": {
				"type": "plain_text_input",
				"action_id": "confirm_cancelled_email_template"
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
			"block_id": "confirm_waitlist_email_template_block",
			"element": {
				"type": "plain_text_input",
				"action_id": "confirm_waitlist_email_template"
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
			"block_id": "confirm_rejected_email_template_block",
			"element": {
				"type": "plain_text_input",
				"action_id": "confirm_rejected_email_template"
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
			"block_id":"number_of_roles_block",
			"element": {
				"type": "number_input",
				"is_decimal_allowed": false,
				"action_id": "num_roles",
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
}       '''
    }

}
