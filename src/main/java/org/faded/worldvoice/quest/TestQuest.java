package org.faded.worldvoice.quest;

import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.quest.ServerQuestManager;
import org.faded.worldvoice.quest.AdvancedQuestData;
import org.faded.worldvoice.quest.objective.KillMobObjective;
import org.faded.worldvoice.quest.reward.ItemReward;

import java.util.List;
import java.util.UUID;

public class TestQuest {
    // Example: Create a quest
    AdvancedQuestData quest = new AdvancedQuestData(
            UUID.randomUUID(),
            "First Quest",
            "Kill 10 zombies",
            QuestTab.DAILY,
            QuestStatus.AVAILABLE,
            List.of(
                    new KillMobObjective(
                            new ResourceLocation("minecraft", "zombie"),
                            10,
                            0
                    )
            ),
            List.of(
                    new ItemReward(
                            new ResourceLocation("minecraft", "diamond"),
                            5
                    )
            )
    );
}
