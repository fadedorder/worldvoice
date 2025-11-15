package org.faded.worldvoice.quest;

import net.minecraft.network.chat.Component;

public enum QuestTab {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    ONE_TIME("One-Time"),
    SPECIAL("Special"),
    EVOLUTION("Evolution");

    private final String displayName;

    QuestTab(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Component getComponent() {
        return Component.literal(displayName);
    }
}
