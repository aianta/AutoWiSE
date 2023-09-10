package ca.ualberta.autowise.scripts.slack

import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.view.View

import java.util.stream.IntStream
import java.util.stream.Stream;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;

/**
 * @Author Alexandru Ianta
 *
 * Generate a roles modal allowing users to define roles for a recruitment campaign.
 */
class RolesBlock {

    def static makeView(int numRoles){

        ArrayList<LayoutBlock> blocks = new ArrayList()
        IntStream.iterate(1, i->i+1)
            .limit(numRoles)
            .forEach {index->
                blocks.addAll(makeRoleBlocks(index))
            }

        return view(view->

            view.callbackId("roles")
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle {title->title.type("plain_text").text("Event Roles").emoji(true)})
                .submit(viewSubmit {submit->submit.type("plain_text").text("Submit").emoji(true)})
                .close(viewClose {close->close.type("plain_text").text("Cancel").emoji(true)})
                .blocks(blocks))

    }

    private static List<LayoutBlock> makeRoleBlocks(int index){
        ArrayList<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(divider())
        blocks.add(makeRoleNameField(index))
        blocks.add(makeRoleDescriptionField(index))
        blocks.add(makeNumberOfShiftsField(index))
        blocks.add(divider())

        return blocks
    }

    private static LayoutBlock makeRoleNameField(int index){
        return input(input->{
            input.blockId("role_${index}_name_block")
            input.element( plainTextInput { element->
                element
                        .multiline(false)
                        .actionId("role_${index}_name")
                        .placeholder(plainText("Role ${index} name..."))
            })
            .label(plainText("Role ${index} Name:"))
        })

    }

    private static LayoutBlock makeRoleDescriptionField(int index){
        return input(input->{
            input.blockId("role_${index}_description_block")
            .element(plainTextInput {element->{
                element.multiline(true)
                .placeholder(plainText("Role ${index} description..."))
                .actionId("role_${index}_description")
            }})
            .label(plainText("Role ${index} Description:"))
        })
    }

    private static LayoutBlock makeNumberOfShiftsField(int index){
        return input {input->{
            input.blockId("role_${index}_num_shifts_block")
            .element(numberInput {element->{
                element.actionId("role_${index}_num_shifts")
                        .decimalAllowed(false)
                        .initialValue("1")
                        .maxValue("12")
                        .minValue("1")

            }})
            .label(plainText("# of shifts for this role"))
        }}

    }
}
