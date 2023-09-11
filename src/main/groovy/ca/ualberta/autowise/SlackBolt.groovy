package ca.ualberta.autowise

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventBuffer
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import ca.ualberta.autowise.scripts.google.EventSlurper
import ca.ualberta.autowise.scripts.slack.NewCampaignBlock
import ca.ualberta.autowise.scripts.slack.RolesBlock
import ca.ualberta.autowise.scripts.slack.ShiftsBlock
import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.context.builtin.ViewSubmissionContext
import com.slack.api.bolt.handler.builtin.GlobalShortcutHandler
import com.slack.api.bolt.jetty.SlackAppServer
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest
import com.slack.api.bolt.response.Response
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.methods.request.admin.usergroups.AdminUsergroupsListChannelsRequest
import com.slack.api.methods.request.conversations.ConversationsInfoRequest
import com.slack.api.methods.request.users.UsersInfoRequest
import com.slack.api.methods.response.users.UsersInfoResponse
import com.slack.api.socket_mode.SocketModeClient
import groovy.transform.Field
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

import com.slack.api.model.view.View

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream;

import static ca.ualberta.autowise.scripts.slack.BlockingSlackMessage.*
import static ca.ualberta.autowise.scripts.google.CreateEventSheet.*



class SlackBolt implements Runnable{

    static def log = LoggerFactory.getLogger(ca.ualberta.autowise.SlackBolt.class)
    App app;
    SlackAppServer server;
    SocketModeApp socketModeApp;
    JsonObject config;
    def services
    def autoWiSE
    EventBuffer buffer = new EventBuffer();

    SlackBolt( services,  config,  autoWiSE){
        this.config = config
        this.services = services
        this.autoWiSE = autoWiSE

        AppConfig boltConfig = new AppConfig()
        boltConfig.setSigningSecret(config.getString("slack_signing_secret"))
        boltConfig.setSingleTeamBotToken(config.getString("slack_token"))

        this.app = new App(boltConfig)

        this.app.viewSubmission("create_new_campaign_shifts_phase", (req, ctx)->{

            String [] metadata = req.getPayload().getView().getPrivateMetadata().split("\\|");

            Event partialEvent = buffer.get(metadata[0])

            def stateValues = req.getPayload().getView().getState().getValues();

            Role target = partialEvent.roles.stream().filter {it.name.equals(metadata[1])}.findFirst().get();

            target.shifts = IntStream.iterate(1, i->i+1)
                .limit(target.shifts.size())
                .mapToObj{index->
                    def startTime = LocalTime.parse(stateValues.get("shift_${index}_start_time_block").get("shift_${index}_start_time").getSelectedTime(), EventSlurper.shiftTimeFormatter)
                    def endTime = LocalTime.parse(stateValues.get("shift_${index}_end_time_block").get("shift_${index}_end_time").getSelectedTime(), EventSlurper.shiftTimeFormatter)
                    def numVolunteers = Integer.parseInt(stateValues.get("shift_${index}_num_volunteers_block").get("shift_${index}_num_volunteers").getValue())

                    Shift s = new Shift()
                    s.startTime = startTime
                    s.endTime = endTime
                    s.targetNumberOfVolunteers = numVolunteers

                    return s
                }.collect(Collectors.toList())

            Role nextRole = partialEvent.roles.stream().filter {it.shifts.get(0).startTime == null}.findFirst().orElse(null);

            //If there are no more roles to fill, this event is fully configured.
            if(nextRole == null){
                log.info "Finished creating campaign!"

                partialEvent.status = EventStatus.READY

                //Make an event sheet for our glorious new event
                Thread paperwork = new Thread(()->createEventSheet(config, services.googleAPI, "[SLACK-GEN]${partialEvent.getName()}", partialEvent)
                        .onSuccess { log.info "Event sheet generated!"
                            //TODO: Need to make sure this doesn't have strange implications because it's in a separate thread.
                            //autoWiSE.doExternalTick(services, config)
                        }
                        .onFailure { log.error it.getMessage(), it})

                paperwork.start()

                buffer.remove(partialEvent) //Clear the completed event from the buffer to prevent memory leaks.

                return ctx.ack()
            }else{
                def nextView = ShiftsBlock.makeView(nextRole.name, nextRole.shifts.size(), partialEvent);
                nextView.setPrivateMetadata(partialEvent.getId().toString() + "|" + nextRole.getName());
                return ctx.ack("update", nextView )
            }

        })


        this.app.viewSubmission("create_new_campaign_roles_phase",  (req, ctx)->{

            Event partialEvent = buffer.get(req.getPayload().getView().getPrivateMetadata())

            def stateValues = req.getPayload().getView().getState().getValues();


            //Overwrite blank roles with info from the modal
            partialEvent.roles = IntStream.iterate(1, i->i+1)
                .limit(partialEvent.roles.size())
                .mapToObj (index->{


                    Role r = new Role()

                    def roleName = stateValues.get("role_${index}_name_block").get("role_${index}_name").getValue();
                    def roleDescription = stateValues.get("role_${index}_description_block").get("role_${index}_description").getValue();
                    def numShifts = Integer.parseInt(stateValues.get("role_${index}_num_shifts_block").get("role_${index}_num_shifts").getValue())

                    r.setName(roleName)
                    r.setDescription(roleDescription)
                    r.shifts = new ArrayList<>();

                    Stream.generate {
                        return new Shift()
                    }.limit(numShifts)
                    .forEach {
                        r.shifts.add(it)
                    }

                    return r
                }).collect(Collectors.toList());

            Role firstRole =  partialEvent.roles.get(0)

            def nextView = ShiftsBlock.makeView(firstRole.name, firstRole.shifts.size(), partialEvent);
            nextView.setPrivateMetadata(partialEvent.getId().toString() + "|"+firstRole.name);

            return ctx.ack("update", nextView )


        } )

        this.app.viewSubmission("create_new_campaign", (req, ctx)->{

            //Extract info from modal
            Event partialEvent = populateStaticEventData(req, ctx);
            //Register the partial event into our event buffer
            buffer.add(partialEvent);

            def nextView = RolesBlock.makeView(partialEvent.roles.size())
            nextView.setPrivateMetadata(partialEvent.getId().toString());

            return ctx.ack("update", nextView)

        })

        this.app.command("/new_vol_recruit_campaign", (req, ctx) -> {

            def response = ctx.client().viewsOpen{
                it.triggerId(req.getPayload().getTriggerId())
                        .viewAsString(NewCampaignBlock.viewString())
            }

            if(response.isOk()){
                return ctx.ack()
            }else{
                return Response.builder().statusCode(500).body(response.getError()).build()
            }

        })

        this.app.command("/roles_test", (req, ctx)->{

            def response = ctx.client().viewsOpen{
                it.triggerId(req.getPayload().getTriggerId())
                .view(RolesBlock.makeView(3))
            }

            if(response.isOk()){
                return ctx.ack()
            }else{
                return Response.builder().statusCode(500).body(response.getError()).build()
            }
        })

        this.app.command("/shifts_test", (req,ctx)->{

            def response = ctx.client().viewsOpen{
                it.triggerId(req.getPayload().getTriggerId())
                .view(ShiftsBlock.makeView("Grill Master", 3, null))
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
    Event populateStaticEventData(ViewSubmissionRequest request, ViewSubmissionContext ctx){
        Event result = new Event()
        result.setId(UUID.randomUUID())

        def stateValues = request.getPayload().getView().getState().getValues();
        String eventName = stateValues.get("event_name_block").get("event_name").getValue();
        String eventDescription = stateValues.get("event_description_block").get("event_description").getValue();

        //These are gonna be user strings, have to resolve them into emails.
        List<String> organizers = resolveEmailsFromUserIds(stateValues.get("event_organizers_block").get("event_organizers").getSelectedUsers())


        //These are gonna be user strings, have to resolve them into emails.
        List<String> volunteerCoordinators = resolveEmailsFromUserIds(stateValues.get("volunteer_coordinators_block").get("volunteer_coordinators").getSelectedUsers())

        ZonedDateTime startTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long)stateValues.get("event_start_block").get("event_start").getSelectedDateTime()),AutoWiSE.timezone);
        ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long)stateValues.get("event_end_block").get("event_end").getSelectedDateTime()),AutoWiSE.timezone);

        String eventbriteLink = stateValues.get("eventbrite_block").get("eventbrite_link").getValue();

        String eventSlackChannel = resolveChannelNamefromId(stateValues.get("event_slack_channel_block").get("event_channel").getSelectedChannel());

        ZonedDateTime campaignStartTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(stateValues.get("campaign_start_block").get("campaign_start_datetime").getSelectedDateTime()), AutoWiSE.timezone);

        long campaignStartOffset = ChronoUnit.MILLIS.between(campaignStartTime, startTime)

        log.info "campaign start offset: ${campaignStartOffset}"

        long resolicitFrequency = Duration.ofDays(Long.parseLong(stateValues.get("resolicit_frequency_block").get("resolicit_frequency").getValue())).toMillis();

        ZonedDateTime followupDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(stateValues.get("followup_datetime_block").get("followup_datetime").getSelectedDateTime()), AutoWiSE.timezone)

        long followupOffset = ChronoUnit.MILLIS.between(followupDateTime, startTime)

        log.info "followup offset: ${followupOffset}"

        String initialRecruitmentEmailTemplateId = stateValues.get("initial_recruitment_email_template_block").get("initial_recruitment_email_template").getValue();
        String recruitmentEmailTemplateId = stateValues.get("recruitment_email_template_block").get("recruitment_email_template").getValue();
        String followupEmailTemplateId = stateValues.get("followup_email_template_block").get("followup_email_template").getValue();
        String confirmAssignedEmailTemplateId = stateValues.get("confirm_assigned_email_template_block").get("confirm_assigned_email_template").getValue();
        String confirmCancelledEmailTemplateId = stateValues.get("confirm_cancelled_email_template_block").get("confirm_cancelled_email_template").getValue();
        String confirmWaitlistEmailTemplateId = stateValues.get("confirm_waitlist_email_template_block").get("confirm_waitlist_email_template").getValue();
        String confirmRejectedEmailTemplateId = stateValues.get("confirm_rejected_email_template_block").get("confirm_rejected_email_template").getValue();

        //TODO - validation

        int numberOfRoles = Integer.parseInt(stateValues.get("number_of_roles_block").get("num_roles").getValue());

        //Init roles list
        result.roles = new ArrayList<>();
        //Create blank roles for this event to be filled in the next step
        Stream.generate {
            return new Role()
        }.limit(numberOfRoles)
        .forEach {result.roles.add(it)}

        result.setName(eventName);
        result.setDescription(eventDescription)
        result.setEventOrganizers(organizers)
        result.setVolunteerCoordinators(volunteerCoordinators)
        result.setStartTime(startTime)
        result.setEndTime(endTime)
        result.setEventbriteLink(eventbriteLink)
        result.setEventSlackChannel(eventSlackChannel)
        result.setCampaignStartOffset(campaignStartOffset)
        result.setResolicitFrequency(resolicitFrequency)
        result.setFollowupOffset(followupOffset)
        result.setInitialRecruitmentEmailTemplateId(initialRecruitmentEmailTemplateId)
        result.setRecruitmentEmailTemplateId(recruitmentEmailTemplateId)
        result.setFollowupEmailTemplateId(followupEmailTemplateId)
        result.setConfirmAssignedEmailTemplateId(confirmAssignedEmailTemplateId)
        result.setConfirmWaitlistEmailTemplateId(confirmWaitlistEmailTemplateId)
        result.setConfirmCancelledEmailTemplateId(confirmCancelledEmailTemplateId)
        result.setConfirmRejectedEmailTemplateId(confirmRejectedEmailTemplateId)

        return result;
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
                return response.getUser().getProfile().getEmail() //TODO - what if there is no email? Can that even happen?
            })
        .collect(Collectors.toList())
    }
}
