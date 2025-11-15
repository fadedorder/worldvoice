package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class CommandReward implements QuestReward {
    private final String command;
    private final String displayText;

    public CommandReward(String command) {
        this(command, null);
    }

    public CommandReward(String command, String displayText) {
        this.command = command;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public String getCommand() {
        return command;
    }

    @Override
    public QuestRewardType<?> getType() {
        return QuestRewardType.COMMAND;
    }

    @Override
    public Component getDisplayComponent() {
        String text = displayText != null ? displayText : "Execute: " + command;
        return Component.literal(text);
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText : "Command: " + command;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "command");
        json.addProperty("command", command);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(command);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static CommandReward fromJson(JsonObject json) {
        String command = json.get("command").getAsString();
        String display = QuestDisplayUtil.readDisplay(json);
        return new CommandReward(command, display);
    }

    public static CommandReward fromNetwork(FriendlyByteBuf buf) {
        String command = buf.readUtf();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new CommandReward(command, display);
    }
}
