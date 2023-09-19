# AutoWiSE

# TODO
* ~~Futurize operations~~
  * Error handling, especially with API calls.
    * Handle GoogleAPI 401 - unauthenticated using slack.
    * ~~Add an autowise technical channel, and send errors there~~
    * Ideally fail safe
  * ~~Futurize operations done on tick, a tick should complete a future.~~
* ~~Canary Slack Message~~
* ~~Switch to MDT/MST for all timestamps~~
* ~~Send a confirmation response to the user clicking a webhook link sooner (before the action has been processed).~~
* ~~Possible warning appears on links sent to yahoo mail addresses.~~ 
  * ~~Implement SSL/https with certbot~~
* ~~Add web request logging with vertx handler.~~
* Ensure all operations produce meaningful logs.
  * ~~At least success/failure messages.~~
* ~~Campaign registration email updates~~
  * ~~Concat all email templates~~
  * ~~Add a pending/approved stage to all campaigns.~~
* Add a thank you stage post-event.
* Finish Readme
  * Include instructions for setting up an event through Google Sheets
* Creating events using JSON.
* Creating events via slack. 
* ~~Log web requests~~
* Add List-unsubscribe header only to recruitment emails.
~~* Abort a campaign if the underlying event sheet is no longer accessible.~~
* Add template descriptions in this readme. 
* Don't break if there are no volunteer coordinators or event organizers. But break if there are neither.
* Don't create events that happen in the past. 
* ~~Use static variables in slack modal construction.~~
* New Campaign Validation
* Integrate Events into database model
* Slack Campaign Creation Test (Target: Last week of September 2023)
* Add instructions for SSL certificate renewal/creation
* Slack Control API 
  * List active campaigns
  * List scheduled campaign tasks
  * Reschedule campaign task
  * Cancel active campaign
  * Cancel scheduled campaign tasks
  * Revoke volunteer webhook
  * Revoke campaign webhooks.


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

| Validation                                                                    | Implemented | 
|-------------------------------------------------------------------------------|-------------|
| Start time is in the future                                                   | Yes         |
| End time is in the future                                                     | Yes         |
| End time is after start time                                                  | Yes         |
| Recruitment Campaign start time must be in the future                         | Yes         |
| Recruitment Campaign start time must be at least 48h before event start time. | Yes         |
| Role names must be unique                                                     | Yes         |
| Make sure no apps/bots in volunteer coordinator/event organizer lists         | Yes         |
| No special characters in event name.                                          | Yes         |
| Ensure all templateIds resolve to a google doc                                | No          |
| Ensure shift start time is before shift end time                              | No |
| Make sure followup date is in the future                                      | No | 



