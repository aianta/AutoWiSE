# AutoWiSE
[![Build Status](https://dev.azure.com/UAlberta-SSRG/WiSER%20Automation/_apis/build/status%2Faianta.AutoWiSE?branchName=main)](https://dev.azure.com/UAlberta-SSRG/WiSER%20Automation/_build/latest?definitionId=1&branchName=main)

AutoWiSE is a custom automation tool built for the (Women in Science, Engineering & Research) WiSER 
group at the University of Alberta.

# Automated Volunteer Recruitment 

Larger events such as the WiSER summer BBQ, industry mixers, and anniversary galas require many helping hands to
go smoothly. Additionally, volunteering at these events provides an excellent opportunity for interested individuals
to learn more about WiSER and get involved. To this end WiSER maintains a pool of 
individuals who have indicated that they are interested in helping out with the logistics of WiSER events. This 
software automates the recruitment of volunteers from this pool for upcoming WiSER events.

# Usage

1. Create a new volunteer recruitment campaign.
2. Begin the created campaign.
3. Monitor Slack and the generated campaign spreadsheet for progress updates.

## Creating New Recruitment Campaigns

Autowise integrates with Slack to allow the simple creation of volunteer recruitment campaigns.

1. To create a new campaign, open the slack workspace into which Autowise has been integrated.
>[!IMPORTANT]
>If you are
unsure how to access this slack workspace reach out to the executive volunteer coordinators. 
2. In any public channel of the workspace type in `/new_vol_recruit_campaign` in the message box and hit enter.

<img src="/docs/slack-command.gif" alt="animation showing the invocation of the create new volunteer recruitment campaign slack command" height="300">

>[!TIP]
> After typing in just `/n`, `/new_vol_recruit_campaign` should appear as an auto complete option.
3. A new modal will appear prompting you to enter the required information for the recruitment campaign. Fill in all the required fields, then click submit.

<img src="/docs/new-campaign-modal.png" alt="the create campaign modal" width="300">

>[!NOTE]
> For a detailed breakdown of the information required see: [Configuring Recruitment Campaigns](#configuring-recruitment-campaigns)

>[!CAUTION]
> Double-check all information that you enter on each screen as you **will not** be able to go back and change anything. If you realize
> you've made a mistake you will have to create a new campaign. 

That's it! Once you've submitted all the required campaign information the volunteer coordinators and event leads listed for the campaign
will receive an email with all the campaign details. They will need to begin the campaign for anything else to happen. 

## Beginning a created Campaign
Once a campaign has been created, the volunteer coordinators and event leads listed during the campaign creation process will receive an email
with the campaign details. At this stage a campaign should be considered *planned* but not *approved* or *active*. 

This email will contain:

* A break-down the automated recruitment plan Autowise has generated.
* Samples of all emails that volunteers might receive during the campaign.
* A link to the generated **campaign spreadsheet** where progress updates will be made.
* A link to actually begin the execution of the campaign as described in the email. 
* A link to cancel the campagin at any time. 
* Links to cancel any automated task planned for the campaign.
* Links to immediately execute any automated task planned for the campaign. 

For more details on the contents and functionality of the email see: [Understanding the Automated Campaign Plan Email](#understanding-the-automated-campaign-plan-email)

>[!IMPORTANT]
> This is the **last** opportunity to prevent typos/erroneous/out-of-date information from being sent out to the full WiSER general volunteer pool. 

Check the campaign details *carefully* for any errors or out of date information. 

>Do the eventbrite links work correctly?

If everything looks good. Click the `Begin Campaign` link at the bottom of the email. 

>[!NOTE]
> A recruitment campaign will **not** be executed if an event lead or volunteer coordinator has not clicked the `Begin Campaign` link. 

>[!WARNING]
> `Begin Campaign` links expire 72 hours after the creation of the campaign. 
> 
>The campaign will have to be recreated for a new link to be generated.

## Monitoring Campaign Progress
After the campaign has been created, and an event lead or volunteer coordinator has begun the campaign (see: [Beginning a created Campaign](#beginning-a-created-campaign)),
AutoWiSE will execute the planned campaign tasks and manage responses from volunteers.

![](/docs/slack-progress-updates.png)

As it does so, it will update the [campaign spreadsheet](#understanding-the-generated-campaign-spreadsheet) on Google Drive, as well as send progress updates to the campaign Slack channel. 




>![NOTE]
>The campaign spreadsheet is linked in the [Automated Campaign Plan](#understanding-the-automated-campaign-plan-email) email. 

# Configuring Recruitment Campaigns

# Understanding the Automated Campaign Plan Email

# Understanding the generated Campaign Spreadsheet

# Configuration

```yaml
---
# General
external_tick_rate: 10800000 # How often does AutoWiSE execute its external loop (the one that checks for new email templates on GDrive)
internal_tick_rate: 30000 # How often does AutoWiSE execute its internal loop (the one that dispatches planned tasks from SQLite)
mass_email_delay: 1000 # Time in ms between emails in a mass send task. Need to keep below 2 emails/second.
sheet_update_delay: 15000 # Time in ms to wait after task/webhook before updating event spreadsheet values.

# SQLite Config
db_connection_string: 'jdbc:sqlite:data/autowise.db'

# Web Server Config
host: localhost
port: 8001
jks_path: conf/autowise-local.jks # path to the keystore
jks_password: autowise # keystore password
protocol: https

# Google API Configs
application_name: AutoWiSE
credentials_path: conf/credentials.json
auth_tokens_directory_path: conf/tokens
auth_server_host: localhost
auth_server_receiver_port: 8002

# Gmail Config
sender_email: <email address to appear as sender> # Needs to be set properly to avoid triggering spam filters

# Google Drive Configs
autowise_event_prefix: "[AutoWiSE][Event]"
autowise_drive_folder_id: 14kqs83NGNJYOtzhx6iG2sJoR9MTi6f4N # Root autowise folder id on google drive
autowise_volunteer_pool_id: 1V-f7hhY2_nsoSOGgPKDmUCpScjFACo1TWNaGY6hQBIs # Drive id of the sheet containing the volunteer list
autowise_volunteer_table_range: Sheet1!A:B
autowise_new_recruitment_campaign_email_template: 1HhYJ2HX0aZ2IFbowzQXF7k1uUSIAT-ASazliGog9mjc #Google Doc id of the template to use when a new recruitment campaign is created.
autowise_event_template_sheet: 1yhB6ynS2b769oy7dQ--0lsJ_xhZaDr1VBolxb4Ci8UQ

# Sheet clearing ranges, these are the ranges that are cleared when a new campaign is registered. They do not include headers.
volunteer_contact_status_clearing_range: "'Volunteer Contact Status'!A2:H"
event_status_clearing_range: "'Event Status'!A5:E"
confirmation_status_clearing_range: "'Volunteer Confirmations'!A2:D"
max_roles_per_event: 12

# Slack Config
slack_token: <from OAuth & Permissions section of app control panel>
slack_signing_secret: <from basic information part of app control panel>
technical_channel: "#autowise-technical"
socket_mode_token: <from App-Level tokens part of app control panel>
...
```


# TODO
* ~~Futurize operations~~
  * ~~Error handling, especially with API calls.~~
    * ~~Handle GoogleAPI 401 - unauthenticated using slack.~~
    * ~~Add an autowise technical channel, and send errors there~~
    * ~~Ideally fail safe~~
  * ~~Futurize operations done on tick, a tick should complete a future.~~
* ~~Canary Slack Message~~
* ~~Switch to MDT/MST for all timestamps~~
* ~~Send a confirmation response to the user clicking a webhook link sooner (before the action has been processed).~~
* ~~Possible warning appears on links sent to yahoo mail addresses.~~ 
  * ~~Implement SSL/https with certbot~~
* ~~Add web request logging with vertx handler.~~
* ~~Ensure all operations produce meaningful logs.~~
  * ~~At least success/failure messages.~~
* ~~Campaign registration email updates~~
  * ~~Concat all email templates~~
  * ~~Add a pending/approved stage to all campaigns.~~
* Add a thank you stage post-event.
* Creating events using JSON.
* ~~Creating events via slack.~~
* ~~Log web requests~~
* Add List-unsubscribe header only to recruitment emails.
* ~~Abort a campaign if the underlying event sheet is no longer accessible.~~
* Add template descriptions in this readme.
* ~~Don't create events that happen in the past.~~ 
* ~~Use static variables in slack modal construction.~~
* ~~New Campaign Validation~~
* ~~Integrate Events into database model~~
* ~~Slack Campaign Creation Test (Target: Last week of September 2023)~~
* ~~Add instructions for SSL certificate renewal/creation~~
* ~~Campaign created modal view~~
* ~~Give AutoWiSE permissions to post to any public channel.~~
* ~~Register campaign on creation completion.~~ 
* ~~Include link to spreadsheet in registration email.~~
* **Do not allow Recruitment emails after the follow-up confirmation email.** 
* ~~Fix the class cast exception that happens on webhook invokes.~~ 
* ~~Notify on Slack channel when a volunteer confirms their shift role.~~
* **Improve Slack Campaign creation with back/forward buttons and a confirmation phase.**
* Refactor new campaign validation logic to remove duplicate code.
* Slack Control API 
  * List active campaigns
  * List scheduled campaign tasks
  * Reschedule campaign task
  * Cancel active campaign
  * Cancel scheduled campaign tasks
  * Revoke volunteer webhook
  * Revoke campaign webhooks.
* Support volunteer for any shifts function
* Enhance heartbeat function with basic reporting:
  * Next event
  * Next tasks
  * Last executed tasks
  * Last invoked webhooks
* Create a slack command to force an event spreadsheet update

# Building Docker image

`docker build . -t aianta/autowise`

# Running with Docker
## Locally on a windows machine

`docker run -p 8001:8001 -p 8002:8002 -v .\conf\:/home/autowise/conf -v .\data\:/home/autowise/data aianta/autowise`

## On Linux server (Prod)
`docker run -p 8001:8001 -p 8002:8002 -v <absolute-path>:/home/autowise/conf -v <absolute-path>:/home/autowise/data aianta/autowise`

## On Hypathia with aianta user

`docker run -p 8001:8001 -p 8002:8002 --rm -v /home/aianta/autowise/conf/:/home/autowise/conf -v /home/aianta/autowise/data/:/home/autowise/data -d --name autowise aianta/autowise`


# SSL Setup 

In order to produce links that don't trip spam filders and warnings, Autowise needs to have a properly configured SSL certificate.

To achieve this for free we request SSL certificates from Let's Encrypt via certbot. These expire every 90 days and must be updated 
at that interval.

## Certificate Creation/Renewal via DNS challenge
At the time of writing the autowise service is using a domain registered through GoDaddy. The simplest way to get a certificate for free
is using certbot with the DNS challege mode. At a highlevel the idea will be to log into to the GoDaddy domain control panel and add in a TXT
record to the DNS table. The TXT record will contain a value given to us by certbot. Then Certbot will independently verify the DNS record and
if the value matches the one it gave us it will generate a certificate for us for that domain.

### Procedure (On Windows)
> Relevant certbot [documentation](https://eff-certbot.readthedocs.io/en/stable/using.html#managing-certificates). Tip: `ctrl+f` for `--manual`
1. Install certbot ([Instructions](https://certbot.eff.org/instructions?ws=other&os=windows))
2. In a shell terminal with admin privileges run the following command
> Note: `autowise.services` is the registered production domain for Autowise at the time of writing.

`certbot certonly --manual --preferred-challenges dns --debug-challenges -d autowise.services`

You should get output that looks something like this in the terminal window:
```
Renewing an existing certificate for autowise.services

- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
Please deploy a DNS TXT record under the name:

_acme-challenge.autowise.services.

with the following value:

WlyC9zUxUbaQ7MsI_kOeA-Z8jx4AYzgCqXYH7Gyf-k8

Before continuing, verify the TXT record has been deployed. Depending on the DNS
provider, this may take some time, from a few seconds to multiple minutes. You can
check if it has finished deploying with aid of online tools, such as the Google
Admin Toolbox: https://toolbox.googleapps.com/apps/dig/#TXT/_acme-challenge.autowise.services.
Look for one or more bolded line(s) below the line ';ANSWER'. It should show the
value(s) you've just added.
```

>Note: On the GoDaddy DNS control panel the `key` for the TXT value should actually be `_acme-challenge` **NOT** `_acme-challenge.autowise.services.` as the above instructions suggest.

Follow the given instructions to set an appropriate TXT DNS record for the domain where Autowise will be deployed.

3. Hit `enter` twice and you should get something like this:

```
Successfully received certificate.
Certificate is saved at: <path-to-cert>\autowise.services\fullchain.pem
Key is saved at:         <path-to-key>\autowise.services\privkey.pem
This certificate expires on 2024-01-27.
These files will be updated when the certificate renews.

NEXT STEPS:
- This certificate will not be renewed automatically. Autorenewal of --manual certificates requires the use of an authentication hook script (--manual-auth-hook) but one was not provided. To renew this certificate, repeat this same certbot command before the certificate's expiry date.

- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
If you like Certbot, please consider supporting our work by:
 * Donating to ISRG / Let's Encrypt:   https://letsencrypt.org/donate
 * Donating to EFF:                    https://eff.org/donate-le
- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
```

4. Navigate to the `<path-to-cert>` in your terminal then follow the steps below to generate the JKS with the new certificate.

## Making a JKS


https://stackoverflow.com/questions/16062072/how-to-add-certificate-chain-to-keystore

```
cat cert.pem chain.pem fullchain.pem >all.pem
openssl pkcs12 -export -in all.pem -inkey privkey.pem -out cert_and_key.p12 -name autowise -CAfile chain.pem -caname root
keytool -importkeystore -deststorepass autowise -destkeypass autowise -destkeystore autowise.jks -srckeystore cert_and_key.p12 -srcstoretype PKCS12 -srcstorepass autowise -alias autowise
keytool -import -trustcacerts -alias root -file chain.pem -keystore autowise.jks -storepass autowise
```

Let's encrypt private keys do not have export passwords, however the keytool which we'll have to use in the next step requires a password so enter `autowise` as the password.

https://community.letsencrypt.org/t/how-could-i-get-the-password-for-keyfile/4703

For simplicity, all keystore related passwords are just `autowise`.

## Updating the certificate in production
After you have procured a JKS you'd like to deploy, you must `scp` the jks onto the prod machine and restart Autowise.

By default Autowise expects the jks to be located in the `conf` folder, but the setting is configurable in the `autowise-conf.yaml` file under the key `jks_path`.

# A note on Google OAuth2
The refresh token which is necessary to refresh access tokens that normally expire within 1 hour is only returned on the first authorization.
This means that if you want to use the same Google account to authorize multiple instances of AutoWiSE (say a local dev and the prod version), you will need to manually delete the refresh token from the database.
In practice the refresh token should go to the prod version, as the local dev version should be fine with the hour long access tokens.


# Error Handling

| Source         |Code| Description                                                                                                                                                                                                                                                                                                                                                                                                   | Behavior                                                                                                                                                            | Implemented |
|----------------|-|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| GoogleAPI Call | 404 | Returned when trying to access a resource on google drive (doc, sheet, etc.) that does not or no longer exists. Exhibited if a google sheet for an active event is deleted.                                                                                                                                                                                                                                   | Cancel the entire campaign.                                                                                                                                         | Yes         |    
| GoogleAPI Call | 400 | Bad request                                                                                                                                                                                                                                                                                                                                                                                                   | Report error to technical slack channel.<br/> TODO: Case by case handling if error occurs during:<br/> <ul><li>Scheduled task</li><li>Link triggered task</li></ul> | TODO        |
| GoogleAPI Call | 401 | Invalid credentials                                                                                                                                                                                                                                                                                                                                                                                           | Likely an expired token. Try re-authing through slack. Consider implications of waiting for auth link click during whatever operation was going on.                 | TODO        |
| GoogleAPI Call | 403 | The daily limit was exceeded.<br/>The user rate limit was exceeded.<br/>The project rate limit was exceeded.<br/>The sharing rate limit was exceeded.<br/>The user hasn't granted your app rights to a file.<br/>The user doesn't have sufficient permissions for a file.<br/> Your app can't be used within the signed in user's domain.<br/>Number of items in a folder was exceeded.<br/> [More details](https://developers.google.com/drive/api/guides/handle-errors#resolve_a_403_error) | TODO | TODO        |
| GoogleAPI Call | 429 | Too Many Requests. | Exponential back-off and retry the call. | TODO |
| GoogleAPI Call | 5xx | Google side error | Exponential back-off to 5 minutes, then reschedule operation in 3 hours. | TODO |

# Validation 

List of things to check when creating a new campaign.

| Validation                                                                    | Implemented                      | 
|-------------------------------------------------------------------------------|----------------------------------|
| Start time is in the future                                                   | Yes                              |
| End time is in the future                                                     | Yes                              |
| End time is after start time                                                  | Yes                              |
| End time is on same day as start time                                         | No                               |
| Recruitment Campaign start time must be in the future                         | Yes                              |
| Recruitment Campaign start time must be at least 48h before event start time. | Yes                              |
| Role names must be unique                                                     | Yes                              |
| Make sure no apps/bots in volunteer coordinator/event organizer lists         | Yes                              |
| No special characters in event name.                                          | Yes                              |
| Ensure all templateIds resolve to a google doc                                | Yes* -> as static select options |
| Ensure shift start time is before shift end time                              | Yes                              |
| Make sure followup date is in the future                                      | Yes                              | 
| Verify that the eventbrite link is actually a valid URL                       | No                               |

