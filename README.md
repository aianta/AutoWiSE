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

This document is broken into two sections.

The first is targeted towards users of the AutoWiSE system, this includes WiSER executive committee members, 
event leads, and volunteer coordinators. It includes 'how-to' style information on how to use AutoWiSE for volunteer recruitment.

The second is targeted towards administrators of the AutoWiSE system. It is more technically oriented and  contains information on installing, configuring, deploying, 
and managing the AutoWiSE system. 

To jump to the administrator section click [here](#administrators-guide). 

# User Guide
1. [Usage](#usage)
   1. [Creating New Recruitment Campaigns](#creating-new-recruitment-campaigns)
   2. [Starting a Created Campaign](#starting-a-created-campaign)
   3. [Monitoring Campaign Progress](#monitoring-campaign-progress)
2. [Configuring Recruitment Campaigns](#configuring-recruitment-campaigns)
3. [Understanding the Automated Campaign Plan Email](#understanding-the-automated-campaign-plan-email)
4. [Understanding the Generated Campaign Spreadsheet](#understanding-the-generated-campaign-spreadsheet)
5. [Email Templates](#email-templates)
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

## Starting a created Campaign
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




>[!NOTE]
>The campaign spreadsheet is linked in the [Automated Campaign Plan](#understanding-the-automated-campaign-plan-email) email. 

# Configuring Recruitment Campaigns

The following fields are required to create a recruitment campaign. You will be prompted for each when creating a new recruitment campaign using the `/new_vol_recruit_campaign` slack command.

## Basic Event Information

1. **Event Name** - The name or title of the event. This will appear in the subject line of recruitment emails. 
2. **Description** - This description will appear in the body of the recruitment emails.
3. **Event Organizers** - The organizers of the event. 

>[!IMPORTANT] 
>Event organizers must be part of the slack workspace in which AutoWise operates in order to be selected.

4. **Volunteer Coordinators** - The volunteer coordinators for the event.

>[!IMPORTANT] 
>Volunteer coorginatoes must be part of the slack workspace in which AutoWise operates in order to be selected.

5. **Start Time** - The date and time that the event starts. This will be interpreted as mountain time. 
6. **End Time** - The date and time that the event ends. This will be interpreted as mountain time. 

>[!NOTE] 
> AutoWise was **NOT** designed to accommodate multi-day events, it may break or behave unusually if such use is attempted.

7. **Eventbrite Link** - This should be the url to the eventbrite page for the event, usually starts with `https://www.eventbrite.ca/e/`.

## Basic Recruitment Campaign Settings

8. **Event Slack Channel** - This is the slack channel within the AutoWise slack workspace where AutoWise will send updates regarding the volunteer recruitment campaign. For example, notifying this channel when someone registers to volunteer for a shift.
9. **Start Volunteer Recruitment Campaign on** - This is the date and time at which the first volunteer recruitment email will be sent for this campagin. 
10. **Send periodic reminder recruitment emails every X days** - Here you specify a value for X, if you choose 3 for example, AutoWise will send the initial recruitment campaign email, then 3 days later it will send another recruitment email, 3 days after that it will do so again, and so on until the day of the event.

>[!NOTE] 
> Autowise dynamically adjusts follow-up recruitment emails in 2 important ways:
> 1. It will not send follow-up recruitment emails to volunteers who have already signed up, or already indicated they cannot volunteer for the event. 
> 2. It will not include registration links for shifts/roles that have already met their recruitment quota. 

11. **Follow-up with registed volunteers on** - This is the date and time at which AutoWise will send a follow-up email specifically to volunteers who have registered for a shift/role for the event. They will be prompted to confirm their availability for their chosen shift/role.
>[!NOTE] 
> This date is commonly set pretty close to the event start time. Recruitment campaigns can sometimes start 2 weeks or more before the event. Some volunteers may therefore register and forget they've done so. 
> This followup-confirmation process reminds registered volunteers of their commitment and gives AutoWise and volunteer coordinators an opportunity to find replacements if circumstances have changed and they can no longer make their shfit.

## Email Template Configuration
In most cases you can leave all these settings to their defaults, and move on to [the next section](#recruitment-campaign-roles-shifts-and-quotas) However, if you have created new email templates using google docs, you can configure AutoWise to use those here.

For convenience the default values for each setting is noted with each description so you can check if you're not sure if you accidentally changed one. 

12. **Initial Recruitment Email Template** (Default: `[AutoWiSE] Recruitment Email Template`) - This is the email template that will be used to send the **first** recruitment email of the campaign. It will be sent at the date and time configured in field 9.
13. **Recruitment Email Template** (Default: `[AutoWiSE] Recruitment Email Template`) - This is the email template that will be used in subsequent recruitment emails for the campaign. It will be sent periodically every X number of days until the start date of the event. X is configured in field 10.

>[!NOTE] 
> The initial recruitment email template and recruitment email template are separate settings to allow the use of different wording for these kinds of emails. For example making subsequent recruitment emails start with `Hi Folks, we are still looking for volunteers...` while leaving the initial recruitment email unchanged. By default however the same email template is used for both.

14. **Follow-up Email Template** (Default `[AutoWiSE] Follow-up Template`) - This is the email template that is used to prompt volunteers who have registered for a shift role to confirm their commitment to volunteering a few days prior to the event. See the description for field 11. 
15. **Confirm Assigned Email Template** (Default `[AutoWiSE] Confirm Assigned Email Template`) - This is the email template that will be used to inform volunteers who have clicked a registration link for a shift role, that they have been successfully assigned their desired shift role.
16. **Confirm Cancelled Email Template** (Default `[AutoWiSE] Confirm Cancel Email Template`) - This is the email template that will be used to inform volunteers who have cancelled, on a shift role that they have previously been assigned, that their cancellation request has been processed successfully. 
17. **Confirm Waitlist Email Template** (Default `[AutoWiSE] Confirm Waitlist Email Template`) - This is the email template that will be used to inform volunteers who have attempted to register for a shift role, who's quota has already been filled, that they have been added to the waitlist for that shift role.
18. **Confirm Rejected Email Template** (Default `[AutoWiSE] Confirm Reject Email Template`) - This is the email template that will be ussed to inform volunteers who have rejected volunteering for any shift role for this event, that their rejection has been processed successfully and they will no longer be contacted for recruitment purposes for _this event_. 

## Recruitment Campaign Roles, Shifts and Quotas

19. **Number of volunteer roles** - This is the number of different roles volunteers can have for the event. For example if you'd like to have dedicated volunteers for: 'setup, clean up, and helping attendees' that would be 3 different volunteer roles. 


### Roles
After field 19, you will be prompted to fill in details for each role. So if you specified 3 roles in field 19, you will have to fill in the following 3 times.

**Role Name** - The name of this role. For example `Setup`

**Role description** - The description for this volunteer role. This description will be included in the recruitment emails beside the registration link for this role, so let volunteers know what they would be expected to do if volunteering for this role.

**`#` of shifts for this role** - This is the number of shifts for this role. For example, let's say you're running the Summer BBQ, and the event lasts for 4 hours, you've configured a role for `Grill Master` but want to create 2 shifts for the role one from `10:00am` to `12:00pm` and one from `12:00pm` to `2:00pm`. So that volunteers don't have to commit to the full 4 hours. In this case you'd set this value to 2. 

>[!NOTE] 
> Each role has at minimum 1 shift.

### Shifts
Once you've filled in all role information, you will be prompted to fill in shift details for every role. 

**Start Time** - What time does this shift start. 

**End Time** - What time does this shift end. 

>[!NOTE] 
> You can set start times and end times before and after the start and end times of the event. 

>[!IMPORTANT] 
> However, all shift times will be interpreted as on the day of the event. AutoWiSE does not support multi-day events, or volunteer shifts before or after the day of the event.

**Number of Volunteers for this shift** - This is the quota for this role-shift, the number of volunteers you'd like recruited for this job. So say you want 2 volunteers for this shift. If a third volunteer attempts to sign up, they will be placed on a waitlist. Should one of the volunteers who were assigned for this shift role cancel, AutoWiSE will automatically swap someone from the waitlist into their slot, and notify all relevant parties.

That's it, once you fill in this information for all the shifts and roles you configured the recruitment campaign will be created. As part of this process AutoWise will create a spreadsheet where the state of the recruitment campaign can be tracked (see [this section for more details](#understanding-the-generated-campaign-spreadsheet)). 

>[!IMPORTANT] 
> Your campaign is **NOT** active at this stage. AutoWiSE will **NOT** send any recruitment emails or perform any campaign tasks until an event organizer or volunteer coordinator clicks the `Begin Campaign` link in the [Automated Campaign Plan Email](#understanding-the-automated-campaign-plan-email).  

# Understanding the Automated Campaign Plan Email

After a new recruitment campaign plan is configured and submitted through slack. The people identified as volunteer coordinators and event organizers for the event will receive a detailed campaign plan email.
An example of this campaign plan email is shown below. 

The purpose of this email is to confirm that the campaign details that have been entered are correct, and that the campaign should be executed as described.

>[!IMPORTANT] 
> This is the last point at which incorrect information can be caught before volunteers are notified. 

<img src="/docs/email-sample.png" alt="example of the automated campaign plan email" height="300">

Starting from the top of the email, you will find: 
* The event name (in this case `Industry Mixer 2024`).
* The event description (immediately below the event name).
* The link to the campaign spreadsheet in google docs. 
* A list of automated tasks that will be executed for this campaign along with links that will allow you to cancel any individual tasks or execute them ahead of time. 
* The `Cancel Campaign` link which prevents any further tasks from being executed for this campaign. 
* A sample of every kind of email that will be sent as part of this recruitment campaign. This is what volunteers will receive. 
* The `Begin Campaign` link, which will be located at the very bottom of the email (not pictured). Clicking it will change the status of the campaign from pending to in-progress and the planned tasks will be executed at the times specified in the task table located higher up in the email. 

>[!NOTE] 
> You do not need to cancel recruitment emails once all shift-roles have been filled for an event. AutoWiSE will automatically avoid sending recruitment emails if all shift-roles are filled.


This email also provides volunteer coordinators and event organizers with the ability to execute or cancel any individual campaign action (such as the sending a wave of recruitment emails). Or to cancel the entire campagin. 

>[!IMPORTANT]
> Event organizers and volunteer coordinators can cancel an in-progress recruitment campaign with the `Cancel Campaign` link in this email.

>[!WARNING] 
> A campagin will not be executed unless the `Begin Campaign` link is clicked by a volunteer coordinator or event organizer. 

>[!WARNING] 
> There is no mechanism to determine who has clicked any link in this email. Whoever receives or is forwarded this email will be able to execute any of the actions described.


# Understanding the generated Campaign Spreadsheet

A recruitment campaign spreadsheet has 4 sheets, each described below. You can access these sheets by selecting them 
from the bottom left once you have the spreadsheet open. 

>[!TIP] 
> A link to the spreadsheet generated for a campaign can be found in the [Automated Campaign Plan Email](#understanding-the-automated-campaign-plan-email).

## Event Sheet
<img src="/docs/spreadsheet-event.png" alt="example of the event sheet" height="500">

The event sheet lists the recruitment campaign configuration as it was entered through the slack campaign creation process. 

## Event Status Sheet
<img src="/docs/spreadsheet-event-status.png" alt="example of the event status sheet" height="300">

The event status sheet shows all the shift-roles for the event and which volunteer has been assigned to each shift-role. As the recruitment campaign continues this will be populated with volunteers who register for shift roles.

## Volunteer Contact Status Sheet
<img src="/docs/spreadsheet-volunteer-contact-status.png" alt="example of the volunteer contact status sheet" height="150">

The volunteer contact status sheet shows the interactions between AutoWiSE and every volunteer. Here you can see if a volunteer has been contacted for this campaign, if they have accepted, rejected, or canceled on a shift-role, as well as the timestamps associated with these actions. You are also able to see which volunteers are currently waitlisted for a shift role, and what date-time they were wait listed on. 

## Volunteer Confirmations Sheet
<img src="/docs/spreadsheet-volunteer-confirmations.png" alt="example of the volunteer confirmation sheet" height="300">

The volunteer confirmations sheet keeps track of all volunteers who have confirmed their availability to volunteer for the event. This is different from having registered to volunteer. Confirmations on this sheet will only start appearing once the 'Follow-up Confirmation Email' has been sent. See [Configuring Recruitment Campaigns](#configuring-recruitment-campaigns) field #11 for more details. 

# Email Templates

# Administrator's Guide

## Setting up the Volunteer Pool Sheet

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

