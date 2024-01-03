package ca.ualberta

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.model.EventStatus
import ca.ualberta.autowise.model.Role
import ca.ualberta.autowise.model.Shift
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.Stream

class TestEventGenerator {

    private static final Logger log = LoggerFactory.getLogger(TestEventGenerator.class)
    private static Random r = new Random();

    class EventBuilder{
        UUID id;
        EventStatus status;
        ZonedDateTime startTime;
        ZonedDateTime endTime;
        String name;
        String description;
        ZonedDateTime campaignStartTime;
        ZonedDateTime followupTime;
        String sheetId;
        Long resolicitFrequency //In milliseconds
        String initialRecruitmentEmailTemplateId
        String recruitmentEmailTemplateId
        String followupEmailTemplateId
        String confirmAssignedEmailTemplateId;
        String confirmCancelledEmailTemplateId;
        String confirmWaitlistEmailTemplateId;
        String confirmRejectedEmailTemplateId;
        List<String> eventOrganizers;
        List<String> volunteerCoordinators;
        String eventbriteLink;
        String eventSlackChannel;


        int numberOfRoles = r.nextInt(1,7)
        boolean manualRolesSet = false;
        List<Role> roles;

        void addRole(Role r){
            manualRolesSet = true;
            if(roles == null){
                roles = new ArrayList<>()
            }
            roles.add(r)
        }

        void setId(UUID id) {
            this.id = id
        }

        void setStatus(EventStatus status) {
            this.status = status
        }

        void setStartTime(ZonedDateTime startTime) {
            this.startTime = startTime
        }

        void setEndTime(ZonedDateTime endTime) {
            this.endTime = endTime
        }

        void setName(String name) {
            this.name = name
        }

        void setDescription(String description) {
            this.description = description
        }

        void setCampaignStartTime(ZonedDateTime campaignStartTime) {
            this.campaignStartTime = campaignStartTime
        }

        void setFollowupTime(ZonedDateTime followupTime) {
            this.followupTime = followupTime
        }

        void setSheetId(String sheetId) {
            this.sheetId = sheetId
        }

        void setResolicitFrequency(long resolicitFrequency) {
            this.resolicitFrequency = resolicitFrequency
        }

        void setInitialRecruitmentEmailTemplateId(String initialRecruitmentEmailTemplateId) {
            this.initialRecruitmentEmailTemplateId = initialRecruitmentEmailTemplateId
        }

        void setRecruitmentEmailTemplateId(String recruitmentEmailTemplateId) {
            this.recruitmentEmailTemplateId = recruitmentEmailTemplateId
        }

        void setFollowupEmailTemplateId(String followupEmailTemplateId) {
            this.followupEmailTemplateId = followupEmailTemplateId
        }

        void setConfirmAssignedEmailTemplateId(String confirmAssignedEmailTemplateId) {
            this.confirmAssignedEmailTemplateId = confirmAssignedEmailTemplateId
        }

        void setConfirmCancelledEmailTemplateId(String confirmCancelledEmailTemplateId) {
            this.confirmCancelledEmailTemplateId = confirmCancelledEmailTemplateId
        }

        void setConfirmWaitlistEmailTemplateId(String confirmWaitlistEmailTemplateId) {
            this.confirmWaitlistEmailTemplateId = confirmWaitlistEmailTemplateId
        }

        void setConfirmRejectedEmailTemplateId(String confirmRejectedEmailTemplateId) {
            this.confirmRejectedEmailTemplateId = confirmRejectedEmailTemplateId
        }

        void setEventOrganizers(List<String> eventOrganizers) {
            this.eventOrganizers = eventOrganizers
        }

        void setVolunteerCoordinators(List<String> volunteerCoordinators) {
            this.volunteerCoordinators = volunteerCoordinators
        }

        void setEventbriteLink(String eventbriteLink) {
            this.eventbriteLink = eventbriteLink
        }

        void setEventSlackChannel(String eventSlackChannel) {
            this.eventSlackChannel = eventSlackChannel
        }

        Event build(){
            Event result = new Event();
            result.id = id == null?UUID.randomUUID():id;
            result.status = status == null?generateEventStatus():status
            result.startTime = startTime == null?generateStartTime():startTime
            result.endTime = endTime == null?generateEndTime(result.startTime):endTime
            result.name = name == null?generateName():name
            result.description = description == null?"dummy event description": description
            result.campaignStart = campaignStartTime == null? generateCampaignStartTime():campaignStartTime;
            result.followupTime = followupTime == null? generateFollowupTime(result.startTime):followupTime;
            result.sheetId = sheetId == null?generateSheetId():sheetId;
            result.resolicitFrequency = resolicitFrequency == null? generateResolicitFrequency(): resolicitFrequency;
            result.initialRecruitmentEmailTemplateId = initialRecruitmentEmailTemplateId == null? generateEmailTemplateId(): initialRecruitmentEmailTemplateId
            result.recruitmentEmailTemplateId = recruitmentEmailTemplateId == null? generateEmailTemplateId() : recruitmentEmailTemplateId;
            result.followupEmailTemplateId = followupEmailTemplateId == null? generateEmailTemplateId() : followupEmailTemplateId;
            result.confirmAssignedEmailTemplateId = confirmAssignedEmailTemplateId == null? generateEmailTemplateId() : confirmAssignedEmailTemplateId;
            result.confirmCancelledEmailTemplateId = confirmCancelledEmailTemplateId == null? generateEmailTemplateId(): confirmCancelledEmailTemplateId;
            result.confirmWaitlistEmailTemplateId = confirmWaitlistEmailTemplateId == null? generateEmailTemplateId() : confirmWaitlistEmailTemplateId;
            result.confirmRejectedEmailTemplateId = confirmRejectedEmailTemplateId == null? generateEmailTemplateId(): confirmRejectedEmailTemplateId;
            result.eventOrganizers = eventOrganizers == null? generateEmailList(): eventOrganizers;
            result.volunteerCoordinators = volunteerCoordinators == null? generateEmailList() : volunteerCoordinators;
            result.eventbriteLink = eventbriteLink == null? "www.eventbrite.com": eventbriteLink
            result.eventSlackChannel = eventSlackChannel == null?"auto-wise": eventSlackChannel;

            result.roles = manualRolesSet?roles: generateRoles(numberOfRoles)
            return result;
        }

        List<Role> generateRoles(int numRoles){
            return Stream.generate {
                return new RoleBuilder().build();
            }.limit(numRoles)
            .collect(Collectors.toList())
        }

        EventStatus generateEventStatus(){
            return EventStatus.READY;
        }

        ZonedDateTime generateStartTime(){
            return ZonedDateTime.now().plusDays(r.nextLong(4, 30))
        }

        ZonedDateTime generateEndTime(ZonedDateTime startTime){
            return startTime.plusHours(r.nextInt(1, 4))
        }

        String generateName(){
            def pool = ["Summer BBQ", "Bowling", "Self-Defense Class", "Industry Mixer", "WiSER Gala", "Resume Workshop", "Technical Workshop", "Ping Pong Night"]
            return pool.get(r.nextInt(0, pool.size()))
        }

        ZonedDateTime generateCampaignStartTime(){
            return ZonedDateTime.now().plusHours(1)
        }

        ZonedDateTime generateFollowupTime(ZonedDateTime eventStart){
            return eventStart.minusDays(1)
        }

        //https://www.baeldung.com/java-random-string
        String generateSheetId(){
            int leftLimit = 48; // numeral '0'
            int rightLimit = 122; // letter 'z'
            int targetStringLength = 44;
            Random random = new Random();

            String generatedString = random.ints(leftLimit, rightLimit + 1)
                    .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                    .limit(targetStringLength)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();

            return generatedString;
        }

        long generateResolicitFrequency(){
            return TimeUnit.DAYS.toMillis(r.nextInt(1, 3))
        }

        String generateEmailTemplateId(){
            return generateSheetId()
        }

        List<String> generateEmailList(){
            def pool = ["ianta@ualberta.ca", "aianta03@gmail.com", "test@wiser.com", "example@email.com"]

            return [pool.get(r.nextInt(0, pool.size()))]

        }


    }

    class RoleBuilder{
        String name;
        String description;
        List<Shift> shifts;
        int numberOfShifts = r.nextInt(1, 3)
        boolean manualShiftsSet = false;

        void setName(String name) {
            this.name = name
        }

        void setDescription(String description) {
            this.description = description
        }

        void addShift(s){
            manualShiftsSet = true;
            if (shifts == null){
                shifts = new ArrayList<>();
            }
            shifts.add(s);
        }

        void setShifts(List<Shift> shifts) {
            manualShiftsSet = true
            this.shifts = shifts
        }

        Role build(){
            Role result = new Role()
            result.name = name != null? name: generateRoleName()
            result.description = description != null? description: "dummy role description"
            result.shifts = manualShiftsSet?shifts:generateShifts(numberOfShifts)
            return result;
        }

        List<Shift> generateShifts(int numShifts){
            return Stream.generate {
                return new ShiftBuilder().build()
            }.limit(numShifts)
            .collect(Collectors.toList())
        }

        String generateRoleName(){
            def pool = ["Grill Master", "General Volunteer", "Set-up", "Clean-up", "Food Tables", "Registration Table", "Audio/Video"]
            return pool.get(r.nextInt(0, pool.size()))
        }
    }

    class ShiftBuilder{
        int index = -1;
        LocalTime startTime;
        LocalTime endTime;
        int targetNumberOfVolunteers = -1;

        void setIndex(int index) {
            this.index = index
        }

        void setStartTime(LocalTime startTime) {
            this.startTime = startTime
        }

        void setEndTime(LocalTime endTime) {
            this.endTime = endTime
        }

        void setTargetNumberOfVolunteers(int targetNumberOfVolunteers) {
            this.targetNumberOfVolunteers = targetNumberOfVolunteers
        }

        Shift build(){
            Shift result = new Shift()
            result.index = index != -1? index: generateShiftIndex()
            result.startTime = startTime != null? startTime: generateStartTime()
            result.endTime = endTime != null? endTime: generateEndTime(result.startTime)
            result.targetNumberOfVolunteers = targetNumberOfVolunteers != -1? targetNumberOfVolunteers: generateTargetNumberOfVolunteers()
            return result
        }

        int generateTargetNumberOfVolunteers(){
            return r.nextInt(1, 10)
        }

        LocalTime generateEndTime(LocalTime startTime){
            return startTime.plusMinutes(r.nextLong(15, 180))
        }

        LocalTime generateStartTime(){
            int startHour = r.nextInt(0, 18)
            int startMinute = r.nextInt(0, 59)
            return LocalTime.of(startHour, startMinute)
        }

        int generateShiftIndex(){
            return r.ints(1, 1, 10).toArray()[0]
        }
    }

}
