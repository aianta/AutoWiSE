# AutoWiSE

# TODO
* ~~Futurize operations~~
  * Error handling, especially with API calls.
    * Handle GoogleAPI 401 - unauthenticated using slack.
    * Add an autowise technical channel, and send errors there
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
* ~~Log web requests~~

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

