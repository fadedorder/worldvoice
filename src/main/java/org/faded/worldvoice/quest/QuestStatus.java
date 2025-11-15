package org.faded.worldvoice.quest;

public enum QuestStatus {
    AVAILABLE,     // Quest can be started
    ACTIVE,        // Quest is in progress
    TRACKED,       // Quest is actively tracked
    COMPLETED,     // Quest is completed
    LOCKED         // Quest is locked/unavailable
}
