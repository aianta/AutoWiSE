package ca.ualberta.autowise.scripts.slack.boltapp

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;

class CampaignCreatedBlock {

    def static makeView(){
        return  view( view->
            view.callbackId("campaign_created_phase")
                .type("modal")
                .notifyOnClose(false)
                .title(viewTitle{it.type("plain_text").text("Campaign Created")})
                .close(viewClose {it.type("plain_text").text("Ok")})
                .blocks(asBlocks(
                        section {it.blockId("success_msg")
                                .text(markdownText("Your volunteer recruitment campaign has been created! Volunteer coordinators and event leads should receive a new campaign registration email shortly."))
                        }
                ))
        )
    }

}
