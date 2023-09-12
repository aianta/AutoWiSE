package ca.ualberta.autowise.scripts.slack.boltapp

import ca.ualberta.autowise.model.Event
import ca.ualberta.autowise.scripts.google.EventSlurper
import com.slack.api.model.block.LayoutBlock

import java.time.Instant
import java.util.stream.IntStream

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;

/**
 * @Author Alexandru Ianta
 *
 * Generate a shifts modal allowing users to define shifts for a particular role in a recruitment campaign.
 */
class ShiftsBlock {

    static def makeView(String roleName, int numShifts, Event e){

        ArrayList<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(
                section {it.text(markdownText("Configure *${roleName}* shifts for\n\n${e.getName()} from\n${e.getStartTime().format(EventSlurper.eventTimeFormatter)} to ${e.getEndTime().format(EventSlurper.eventTimeFormatter)}."))}
        )

        blocks.add(divider())

            IntStream.iterate(1, i->i + 1)
                    .limit(numShifts)
                    .forEach(index->{
                        blocks.add(section {it.text(markdownText("*${roleName}* Shift ${index}"))})

                        blocks.add(
                            input(input->{
                                input.blockId("shift_${index}_start_time_block")
                                .element(timePicker {timePicker->timePicker
                                        .actionId("shift_${index}_start_time")
                                })
                                .label(plainText("Start Time:"))
                            })
                        )

                        blocks.add(

                                input(input->input.blockId("shift_${index}_end_time_block")
                                    .element(timePicker {it.actionId("shift_${index}_end_time")})
                                        .label(plainText("End Time:"))
                                )

                        )

                        blocks.add(
                                input(input->{
                                    input.blockId("shift_${index}_num_volunteers_block")
                                    .element(numberInput {it.actionId("shift_${index}_num_volunteers")
                                        .minValue("1")
                                        .maxValue("30")
                                        .initialValue("1")
                                        .decimalAllowed(false)
                                    })
                                    .label(plainText("Number of volunteers for this shift:"))
                                })
                        )

                        blocks.add(divider())


                    })




        return view(view->

                view.callbackId("create_new_campaign_shifts_phase")
                        .type("modal")
                        .notifyOnClose(true)
                        .title(viewTitle {title->title.type("plain_text").text("Shifts Setup").emoji(true)})
                        .submit(viewSubmit {submit->submit.type("plain_text").text("Submit").emoji(true)})
                        .close(viewClose {close->close.type("plain_text").text("Cancel").emoji(true)})
                        .blocks(blocks))

    }



}
