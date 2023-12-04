package ca.ualberta.autowise

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventBuffer
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import ca.ualberta.autowise.scripts.slack.boltapp.CampaignCreatedBlock
import ca.ualberta.autowise.scripts.slack.boltapp.validation.SlackModalValidationError
import ca.ualberta.autowise.scripts.google.EventSlurper
import ca.ualberta.autowise.scripts.slack.boltapp.NewCampaignBlock
import ca.ualberta.autowise.scripts.slack.boltapp.RolesBlock
import ca.ualberta.autowise.scripts.slack.boltapp.ShiftsBlock
import ca.ualberta.autowise.scripts.slack.boltapp.validation.rules.NoSpecialCharacters
import ca.ualberta.autowise.utils.Either
import com.slack.api.Slack
import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.context.builtin.ViewSubmissionContext
import com.slack.api.bolt.jetty.SlackAppServer
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest
import com.slack.api.bolt.response.Response
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.methods.request.conversations.ConversationsInfoRequest
import com.slack.api.methods.request.conversations.ConversationsListRequest
import com.slack.api.methods.request.users.UsersInfoRequest
import com.slack.api.methods.response.users.UsersInfoResponse
import com.slack.api.socket_mode.SocketModeClient
import io.vertx.core.json.JsonObject
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream;

import static ca.ualberta.autowise.scripts.slack.SendSlackMessage.*
import static ca.ualberta.autowise.scripts.google.CreateEventSheet.*
import static ca.ualberta.autowise.scripts.slack.boltapp.NewCampaignBlock.*;
import static ca.ualberta.autowise.scripts.google.GetFilesInFolder.*
import static ca.ualberta.autowise.scripts.RegisterNewEvent.*;



class SlackBolt implements Runnable{

    static def log = LoggerFactory.getLogger(ca.ualberta.autowise.SlackBolt.class)
    App app;
    SlackAppServer server;
    SocketModeApp socketModeApp;
    JsonObject config;
    def services
    def autoWiSE
    EventBuffer buffer = new EventBuffer();
    Set<Pair<String, String>> validGDriveIds = new HashSet<>() //Store all google drive item ids within the Autowise folder. Templates must come from there.
    def newCampaignCommand = "/new_vol_recruit_campaign"

    SlackBolt( services,  config,  autoWiSE){
        this.config = config
        this.services = services
        this.autoWiSE = autoWiSE

        //If running locally, need to use a different command name so as to not clash with prod slackbolt app.
        if (config.getString("host").equals("localhost")){
            newCampaignCommand = "/dnew_vol_recruit_campaign"
        }

        updateValidGDriveIds()

        AppConfig boltConfig = new AppConfig()
        boltConfig.setSigningSecret(config.getString("slack_signing_secret"))
        boltConfig.setSingleTeamBotToken(config.getString("slack_token"))

        this.app = new App(boltConfig)

        this.app.viewSubmission("create_new_campaign_shifts_phase", (req, ctx)->{

            String [] metadata = req.getPayload().getView().getPrivateMetadata().split("\\|");

            Event partialEvent = buffer.get(metadata[0])

            def stateValues = req.getPayload().getView().getState().getValues();

            Role target = partialEvent.roles.stream().filter {it.name.equals(metadata[1])}.findFirst().get();

            def eitherShifts = IntStream.iterate(1, i->i+1)
                .limit(target.shifts.size())
                .mapToObj{index->

                    try{
                        def startTimeBlock = "shift_${index}_start_time_block"
                        def endTimeBlock = "shift_${index}_end_time_block"
                        def numVolunteersBlock = "shift_${index}_num_volunteers_block"

                        def startTime = LocalTime.parse(stateValues.get(startTimeBlock).get("shift_${index}_start_time").getSelectedTime(), EventSlurper.shiftTimeFormatter)
                        def endTime = LocalTime.parse(stateValues.get(endTimeBlock).get("shift_${index}_end_time").getSelectedTime(), EventSlurper.shiftTimeFormatter)
                        def numVolunteers = Integer.parseInt(stateValues.get(numVolunteersBlock).get("shift_${index}_num_volunteers").getValue())

                        SlackModalValidationError validationErrors = new SlackModalValidationError()

                        validationErrors.merge(validate(startTimeBlock, startTime, [
                                {time->time.isBefore(endTime)}: "Start time must be before end time.",
                        ]))

                        validationErrors.merge(validate(endTimeBlock, endTime,  [
                                {time->time.isAfter(startTime)}: "End time must be after start time."
                        ]))

                        if(validationErrors.getErrors().size() > 0){
                            throw validationErrors
                        }


                        Shift s = new Shift()
                        s.index = index
                        s.startTime = startTime
                        s.endTime = endTime
                        s.targetNumberOfVolunteers = numVolunteers

                        return Either.Right(s)
                    }catch (SlackModalValidationError err){
                        return Either.Left(err)
                    }


                }.collect(Collectors.toList())

            def validationErrors = new SlackModalValidationError()
            eitherShifts.stream().filter {it.isLeft()}.collect(Collectors.toList()).forEach {validationErrors.merge(it.getLeft().get())}

            if(validationErrors.getErrors().size() > 0){
                return ctx.ackWithErrors(validationErrors.getErrors())
            }

            target.shifts = eitherShifts.stream().filter {it.isRight()}.map{it.getRight().get()}.collect(Collectors.toList())

            Role nextRole = partialEvent.roles.stream().filter {it.shifts.get(0).startTime == null}.findFirst().orElse(null);

            //If there are no more roles to fill, this event is fully configured.
            if(nextRole == null){
                log.info "Finished creating campaign!"

                partialEvent.status = EventStatus.READY

                //Make an event sheet for our glorious new event
                //Do this in a seprate thread so that the Bolt Socket App doesn't hang/lose connection to slack.
                Thread paperwork = new Thread(()->createEventSheet(config, services.googleAPI, "${config.getString("autowise_event_prefix")}${partialEvent.getName()}", partialEvent)
                        .onSuccess { log.info "Event sheet generated!"
                            //TODO: Need to make sure this doesn't have strange implications because it's in a separate thread.
                            // better yet can we just register the newly created sheet directly?
                            partialEvent.sheetId = it

//                            autoWiSE.doExternalTick(services, config)
                            registerNewEvent(services, partialEvent, it, config)
                        }
                        .onFailure { log.error it.getMessage(), it})

                paperwork.start()

                buffer.remove(partialEvent) //Clear the completed event from the buffer to prevent memory leaks.

                return ctx.ack("update", CampaignCreatedBlock.makeView())
            }else{
                def nextView = ShiftsBlock.makeView(nextRole.name, nextRole.shifts.size(), partialEvent);
                nextView.setPrivateMetadata(partialEvent.getId().toString() + "|" + nextRole.getName());
                return ctx.ack("update", nextView )
            }

        })


        this.app.viewSubmission("create_new_campaign_roles_phase",  (req, ctx)->{


                Event partialEvent = buffer.get(req.getPayload().getView().getPrivateMetadata())

                def stateValues = req.getPayload().getView().getState().getValues();


                        Set<String> namesSoFar= new HashSet<>();

                        def eitherRoles = IntStream.iterate(1, i->i+1)
                        .limit(partialEvent.roles.size())
                        .mapToObj (index->{

                            try{
                                Role r = new Role()

                                def roleNameBlock = "role_${index}_name_block"
                                def roleDescriptionBlock = "role_${index}_description_block"
                                def numShiftsBlock = "role_${index}_num_shifts_block"

                                def roleName = stateValues.get(roleNameBlock).get("role_${index}_name").getValue();
                                def roleDescription = stateValues.get(roleDescriptionBlock).get("role_${index}_description").getValue();
                                def numShifts = Integer.parseInt(stateValues.get(numShiftsBlock).get("role_${index}_num_shifts").getValue())

                                //Start Validation
                                SlackModalValidationError validationErrors = new SlackModalValidationError();

                                validationErrors.merge(validate(roleNameBlock, roleName, [
                                        {name-> return namesSoFar.stream().filter {it.equals(name)}.findFirst().orElse(null) == null}: "There already exists a role with that name, role names must be unique.",
                                ]))

                                if(validationErrors.getErrors().size() > 0){
                                    throw validationErrors
                                }


                                //End Validation
                                namesSoFar.add(roleName)

                                r.setName(roleName)
                                r.setDescription(roleDescription)
                                r.shifts = new ArrayList<>();

                                Stream.generate {
                                    return new Shift()
                                }.limit(numShifts)
                                        .forEach {
                                            r.shifts.add(it)
                                        }

                                return Either.Right(r);

                            }catch (SlackModalValidationError error){
                                return Either.Left(error)
                            }



                        }).collect(Collectors.toList());

                def validationErrors = new SlackModalValidationError();
                eitherRoles.stream().filter {it.isLeft()}.collect(Collectors.toList()).forEach {validationErrors.merge(it.getLeft().get())}
                //If there were validation errors, report them and return.
                if(validationErrors.getErrors().size() > 0){
                    return ctx.ackWithErrors(validationErrors.getErrors())
                }

                //Otherwise, let's get the roles. Overwrite blank roles with info from the modal
                partialEvent.roles = eitherRoles.stream().filter {it.isRight()}.map{it.getRight().get()}.collect(Collectors.toList())

                Role firstRole =  partialEvent.roles.get(0)

                def nextView = ShiftsBlock.makeView(firstRole.name, firstRole.shifts.size(), partialEvent);
                nextView.setPrivateMetadata(partialEvent.getId().toString() + "|"+firstRole.name);

                return ctx.ack("update", nextView )







        } )

        this.app.viewSubmission("create_new_campaign", (req, ctx)->{

            try{
                //Extract info from modal
                Event partialEvent = populateStaticEventData(req, ctx)
                //Register the partial event into our event buffer
                buffer.add(partialEvent);
                def nextView = RolesBlock.makeView(partialEvent.roles.size())
                nextView.setPrivateMetadata(partialEvent.getId().toString());

                return ctx.ack("update", nextView)

            }catch (SlackModalValidationError err){

                return ctx.ackWithErrors(err.getErrors())

            }

        })

        this.app.command(newCampaignCommand, (req, ctx) -> {

            log.info(NewCampaignBlock.viewString(validGDriveIds, getSlackChannels()).toString())

            def response = ctx.client().viewsOpen{
                it.triggerId(req.getPayload().getTriggerId())
                        .viewAsString(NewCampaignBlock.viewString(validGDriveIds, getSlackChannels()))
            }

            if(response.isOk()){
                return ctx.ack()
            }else{
                return Response.builder().statusCode(500).body(response.getError()).build()
            }

        })



        socketModeApp = new SocketModeApp(
                config.getString("socket_mode_token"),
                SocketModeClient.Backend.JavaWebSocket,
                app
        );


        sendSlackMessage(services.slackAPI, config.getString("technical_channel"), "Bolt App online!")
    }

    /**
     * Notifies the slack channel that the bolt app is online and ready to receive commands.
     */
    def notifyOnline(){

    }

    @Override
    void run() {
        socketModeApp.start()
        log.info "I still have access to my variables, look: ${config.encodePrettily()}"
    }

    /**
     * Reads the information in the modal and populates an event with it.
     * @param request
     * @param ctx
     * @return
     */
    Event populateStaticEventData(ViewSubmissionRequest request, ViewSubmissionContext ctx) throws SlackModalValidationError{

        Event result = new Event()
        result.setId(UUID.randomUUID())

        def stateValues = request.getPayload().getView().getState().getValues();
        String eventName = stateValues.get(EVENT_NAME_BLOCK).get(EVENT_NAME_ACTION).getValue();
        String eventDescription = stateValues.get(EVENT_DESCRIPTION_BLOCK).get(EVENT_DESCRIPTION_ACTION).getValue();

        //These are gonna be user strings, have to resolve them into emails.
        List<String> organizers = resolveEmailsFromUserIds(stateValues.get(EVENT_ORGANIZERS_BLOCK).get(EVENT_ORGANIZERS_ACTION).getSelectedUsers())


        //These are gonna be user strings, have to resolve them into emails.
        List<String> volunteerCoordinators = resolveEmailsFromUserIds(stateValues.get(EVENT_VOLUNTEER_COORDINATORS_BLOCK).get(EVENT_VOLUNTEER_COORDINATORS_ACTION).getSelectedUsers())

        ZonedDateTime startTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long)stateValues.get(EVENT_START_BLOCK).get(EVENT_START_ACTION).getSelectedDateTime()),AutoWiSE.timezone);
        ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long)stateValues.get(EVENT_END_BLOCK).get(EVENT_END_ACTION).getSelectedDateTime()),AutoWiSE.timezone);

        String eventbriteLink = stateValues.get(EVENT_EVENTBRITE_BLOCK).get(EVENT_EVENTBRITE_ACTION).getValue();

        String eventSlackChannel = "#"+stateValues.get(EVENT_SLACK_CHANNEL_BLOCK).get(EVENT_SLACK_CHANNEL_ACTION).getSelectedOption().getValue()
        //String eventSlackChannel = resolveChannelNamefromId(stateValues.get(EVENT_SLACK_CHANNEL_BLOCK).get(EVENT_SLACK_CHANNEL_ACTION).getSelectedChannel());

        ZonedDateTime campaignStartTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(stateValues.get(EVENT_CAMPAIGN_START_BLOCK).get(EVENT_CAMPAIGN_START_ACTION).getSelectedDateTime()), AutoWiSE.timezone);


        long resolicitFrequency = Duration.ofDays(Long.parseLong(stateValues.get(EVENT_RESOLICIT_FREQUENCY_BLOCK).get(EVENT_RESOLICIT_FREQUENCY_ACTION).getValue())).toMillis();

        ZonedDateTime followupDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(stateValues.get(EVENT_FOLLOWUP_BLOCK).get(EVENT_FOLLOWUP_ACTION).getSelectedDateTime()), AutoWiSE.timezone)

        String initialRecruitmentEmailTemplateId = stateValues.get(EVENT_INITIAL_RECRUITMENT_EMAIL_TEMPLATE_BLOCK).get(EVENT_INITIAL_RECURITMENT_EMAIL_TEMPLATE_ACTION).getSelectedOption().getValue();
        String recruitmentEmailTemplateId = stateValues.get(EVENT_RECRUITMENT_EMAIL_TEMPLATE_BLOCK).get(EVENT_RECRUITMENT_EMAIL_TEMPLATE_ACTION).getSelectedOption().getValue();
        String followupEmailTemplateId = stateValues.get(EVENT_FOLLOWUP_EMAIL_TEMPLATE_BLOCK).get(EVENT_FOLLOWUP_EMAIL_TEMPLATE_ACTION).getSelectedOption().getValue();
        String confirmAssignedEmailTemplateId = stateValues.get(EVENT_CONFIRM_ASSIGNED_EMAIL_TEMPLATE_BLOCK).get(EVENT_CONFIRM_ASSIGNED_EMAIL_TEMPLATE_ACTION).getSelectedOption().getValue();
        String confirmCancelledEmailTemplateId = stateValues.get(EVENT_CONFIRM_CANCELLED_EMAIL_TEMPLATE_BLOCK).get(EVENT_CONFIRM_CANCELLED_EMAIL_TEMPLATE_ACTION).getSelectedOption().getValue();
        String confirmWaitlistEmailTemplateId = stateValues.get(EVENT_CONFIRM_WAITLIST_EMAIL_TEMPLATE_BLOCK).get(EVENT_CONFIRM_WAITLIST_EMAIL_TEMPLATE_ACTION).getSelectedOption().getValue();
        String confirmRejectedEmailTemplateId = stateValues.get(EVENT_CONFIRM_REJECTED_EMAIL_TEMPLATE_BLOCK).get(EVENT_CONFIRM_REJECTED_EMAIL_TEMPLATE_ACTION).getSelectedOption().getValue();

        int numberOfRoles = Integer.parseInt(stateValues.get(EVENT_NUMBER_OF_ROLES_BLOCK).get(EVENT_NUMBER_OF_ROLES_ACTION).getValue());

        //Init roles list
        result.roles = new ArrayList<>();
        //Create blank roles for this event to be filled in the next step
        Stream.generate {
            return new Role()
        }.limit(numberOfRoles)
        .forEach {result.roles.add(it)}

        //Begin Validation
        SlackModalValidationError validationErrors = new SlackModalValidationError()


        validationErrors.merge(validate(EVENT_NAME_BLOCK, eventName,
                [
                        { s -> new NoSpecialCharacters().test(s.toString())}: "Event title can only contain alphanumerics and spaces.",
                        { s-> s.toString().length() < 300 } : "Event title can not be longer than 300 characters"
                ]
        ))

        validationErrors.merge(validate(EVENT_START_BLOCK, startTime, [
                {time-> ZonedDateTime.ofInstant(Instant.now(), AutoWiSE.timezone).isBefore(time) } : "Event start time must be in the future.",
                {time-> time.isBefore(endTime)}: "Event start time must be before event end time."
        ]))

        validationErrors.merge(validate(EVENT_END_BLOCK, endTime, [
                {time-> ZonedDateTime.ofInstant(Instant.now(), AutoWiSE.timezone).isBefore(time)}: "Event end time must be in the future.",
                {time-> time.isAfter(startTime)}: "Event end time must be after event start time."
        ]))

        validationErrors.merge(validate(EVENT_CAMPAIGN_START_BLOCK, campaignStartTime, [
                {time-> ZonedDateTime.ofInstant(Instant.now(), AutoWiSE.timezone).isBefore(time)}: "Recruitment campaign must start in the future.",
                {time-> time.isBefore(startTime.minusDays(2))}: "Recruitment campaign must start at least 48h before event start time."
        ]))

        validationErrors.merge(validate(EVENT_VOLUNTEER_COORDINATORS_BLOCK, volunteerCoordinators, [
                {list->list.size() > 0}: "Must specify volunteer coordinators. Apps or bots are ignored in this field."
        ]))

        validationErrors.merge(validate(EVENT_ORGANIZERS_BLOCK, organizers, [
                {list->list.size() > 0}: "Must specify event leads/organizers. Apps or bots are ignored in this field."
        ]))

        validationErrors.merge(validate(EVENT_FOLLOWUP_BLOCK, followupDateTime, [
                {time->ZonedDateTime.ofInstant(Instant.now(), AutoWiSE.timezone).isBefore(time)} : "Follow-up date time must be in the future."
        ]))

        if(validationErrors.getErrors().size() > 0){
            throw validationErrors
        }

        //End Validation

        result.setName(eventName);
        result.setDescription(eventDescription)
        result.setEventOrganizers(organizers)
        result.setVolunteerCoordinators(volunteerCoordinators)
        result.setStartTime(startTime)
        result.setEndTime(endTime)
        result.setEventbriteLink(eventbriteLink)
        result.setEventSlackChannel(eventSlackChannel)
        result.setCampaignStart(campaignStartTime)
        result.setResolicitFrequency(resolicitFrequency)
        result.setFollowupTime(followupDateTime)
        result.setInitialRecruitmentEmailTemplateId(initialRecruitmentEmailTemplateId)
        result.setRecruitmentEmailTemplateId(recruitmentEmailTemplateId)
        result.setFollowupEmailTemplateId(followupEmailTemplateId)
        result.setConfirmAssignedEmailTemplateId(confirmAssignedEmailTemplateId)
        result.setConfirmWaitlistEmailTemplateId(confirmWaitlistEmailTemplateId)
        result.setConfirmCancelledEmailTemplateId(confirmCancelledEmailTemplateId)
        result.setConfirmRejectedEmailTemplateId(confirmRejectedEmailTemplateId)


        return result
    }

    private String resolveChannelNamefromId(String channelId){
        def response =  app.client()
                .conversationsInfo(ConversationsInfoRequest.builder()
                .token(config.getString("slack_token"))
                .channel(channelId).build())
        return "#"+response.getChannel().getName();

    }

    private List<String> resolveEmailsFromUserIds(List<String> userIds){
        return userIds.stream()
            .map(userId->{
                UsersInfoResponse response = app.client().usersInfo(UsersInfoRequest.builder().user(userId).token(config.getString("slack_token")).build())
                //TODO API call error handling.
                //Only retrieve real users, ignore app users, bots, workflow bots, etc.
                if(response.getUser().isBot() || response.getUser().isAppUser() || response.getUser().isWorkflowBot() || userId.equals("USLACKBOT")){
                    return Optional.empty()
                }
                log.info "userId: ${userId} -> ${response.getUser()}"
                log.info "userId: ${userId} -> ${response.getUser().getProfile()}"
                log.info "userId: ${userId} -> ${response.getUser().getProfile().getEmail()}"
                return Optional.of(response.getUser().getProfile().getEmail()) //TODO - what if there is no email? Can that even happen?
            })
        .filter {it.isPresent()}
        .map{it.get()}
                collect(Collectors.toList())
    }


    private def validate(blockId, item, Map<Predicate,String> rules){
        SlackModalValidationError errors = new SlackModalValidationError();
        rules.forEach((rule, msg)->{
            log.info "${item}"
            if(!rule(item)){
                errors.addError(blockId, msg)
            }
        })

        return errors;
    }

    def updateValidGDriveIds(){
        Set<Pair<String, String>> result = new HashSet<>();

        //Fetch doc files in Autowise folder to validate new campaign requests with.
        getFiles(services.googleAPI, config.getString("autowise_drive_folder_id"), "application/vnd.google-apps.document").onSuccess {
            files-> files.forEach{
                result.add(Pair.of(it.getId(), it.getName()))
            }
                log.info "new valid GDrive IDs (size: ${validGDriveIds.size()}):"
                result.forEach{log.info "${it.left} - ${it.right}" }

                if (result != null && result.size() > 0){
                    validGDriveIds = result //Only update validGDriveIds if we get a proper result.
                }

        }
    }

    def getSlackChannels(){
        def response = app.client().conversationsList(ConversationsListRequest.builder()
            .token(config.getString("slack_token")).build()
        )

        List<String> result = new ArrayList<>()
        response.getChannels().forEach {result.add(it.name)}

        return result;

    }

    Set<Pair<String, String>> getValidGDriveIds() {
        return validGDriveIds
    }

    void setValidGDriveIds(Set<Pair<String, String>> validGDriveIds) {
        this.validGDriveIds = validGDriveIds
    }
}
