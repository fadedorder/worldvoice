package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.quest.QuestDisplayUtil;

/**
 * Objective requiring the player to collect a certain number of items
 */
public class CollectItemObjective implements QuestObjective {
    private final ResourceLocation itemId;
    private final int requiredCount;
    private int currentCount;
    private final boolean consumeItems;
    private final String displayText;

    public CollectItemObjective(ResourceLocation itemId, int requiredCount, int currentCount, boolean consumeItems) {
        this(itemId, requiredCount, currentCount, consumeItems, null);
    }

    public CollectItemObjective(ResourceLocation itemId, int requiredCount, int currentCount, boolean consumeItems, String displayText) {
        this.itemId = itemId;
        this.requiredCount = requiredCount;
        this.currentCount = currentCount;
        this.consumeItems = consumeItems;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    public boolean shouldConsumeItems() {
        return consumeItems;
    }

    public void setCurrentCount(int count) {
        this.currentCount = Math.min(count, requiredCount);
    }

    @Override
    public QuestObjectiveType<?> getType() {
        return QuestObjectiveType.COLLECT_ITEM;
    }

    @Override
    public float getProgress() {
        return (float) currentCount / requiredCount;
    }

    @Override
    public boolean isCompleted() {
        return currentCount >= requiredCount;
    }

    @Override
    public Component getProgressDisplay() {
        String suffix = consumeItems ? " (Consumed)" : "";
        String label = displayText != null ? displayText :
                "Collect " + itemId.getPath().replace('_', ' ');
        return Component.literal(String.format("%s: %d/%d%s",
                label, currentCount, requiredCount, suffix));
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "collect_item");
        json.addProperty("item_id", itemId.toString());
        json.addProperty("required", requiredCount);
        json.addProperty("current", currentCount);
        json.addProperty("consume", consumeItems);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeResourceLocation(itemId);
        buf.writeInt(requiredCount);
        buf.writeInt(currentCount);
        buf.writeBoolean(consumeItems);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    @Override
    public int getRequiredCount() {
        return requiredCount;
    }

    @Override
    public int getCurrentCount() {
        return currentCount;
    }

    public static CollectItemObjective fromJson(JsonObject json) {
        ResourceLocation itemId = new ResourceLocation(json.get("item_id").getAsString());
        int required = json.get("required").getAsInt();
        int current = json.has("current") ? json.get("current").getAsInt() : 0;
        boolean consume = json.has("consume") && json.get("consume").getAsBoolean();
        String display = QuestDisplayUtil.readDisplay(json);
        return new CollectItemObjective(itemId, required, current, consume, display);
    }

    public static CollectItemObjective fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation itemId = buf.readResourceLocation();
        int required = buf.readInt();
        int current = buf.readInt();
        boolean consume = buf.readBoolean();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new CollectItemObjective(itemId, required, current, consume, display);
    }
}
