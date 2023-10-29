# AutoWiSE
[![Build Status](https://dev.azure.com/UAlberta-SSRG/WiSER%20Automation/_apis/build/status%2Faianta.AutoWiSE?branchName=main)](https://dev.azure.com/UAlberta-SSRG/WiSER%20Automation/_build/latest?definitionId=1&branchName=main)
# TODO
* ~~Futurize operations~~
  * Error handling, especially with API calls.
    * ~~Handle GoogleAPI 401 - unauthenticated using slack.~~
    * ~~Add an autowise technical channel, and send errors there~~
    * Ideally fail safe
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
* Finish Readme
  * Include instructions for setting up an event through Google Sheets
* Creating events using JSON.
* ~~Creating events via slack.~~
* ~~Log web requests~~
* Add List-unsubscribe header only to recruitment emails.
* ~~Abort a campaign if the underlying event sheet is no longer accessible.~~
* Add template descriptions in this readme.
* ~~Don't create events that happen in the past.~~ 
* ~~Use static variables in slack modal construction.~~
* ~~New Campaign Validation~~
* Integrate Events into database model
* Slack Campaign Creation Test (Target: Last week of September 2023)
* Add instructions for SSL certificate renewal/creation
* ~~Campaign created modal view~~
* Automatically add AutoWiSE to the event channel during creation process.
* ~~Register campaign on creation completion.~~ 
* ~~Include link to spreadsheet in registration email.~~
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
| GoogleAPI Call | 403 | The daily limit was exceeded.<br/>The user rate limit was exceeded.<br/>The project rate limit was exceeded.<br/>The sharing rate limit was exceeded.<br/>The user hasn't granted your app rights to a file.<br/>The user doesn't have sufficient permissions for a file.<br/> Your app can't be used within the signed in user's domain.<br/>Number of items in a folder was exceeded.<br/> [More details](https://developers.google.com/drive/api/guides/handle-errors#resolve_a_403_error) | TODO                                                                                                                                                                | TODO        |
| GoogleAPI Call | 429 | Too Many Requests. | Exponential back-off and retry the call.                                                                                                                            | TODO        |
| GoogleAPI Call | 5xx | Google side error | Exponential back-off to 5 minutes, then reschedule operation in 3 hours.                                                                                            | TODO        |
| SocketTimeoutException| - | - | Exponential back-off & retry                                                                                                                                        | Yes         |
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


# Email Templates

Email templates take the form of simple google doc files. 

**IMPORTANT:** Make sure that 'smart quotes' are disabled when creating email templates doc files. This can be done Tools->Preferences and then unticking `Use smart quotes` from the General preferences list. 

Failure to do this will break thymeleaf template parsing. 
