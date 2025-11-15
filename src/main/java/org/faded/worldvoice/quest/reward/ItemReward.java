package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class ItemReward implements QuestReward {
    private final ResourceLocation itemId;
    private final int count;
    private final String displayText;

    public ItemReward(ResourceLocation itemId, int count) {
        this(itemId, count, null);
    }

    public ItemReward(ResourceLocation itemId, int count, String displayText) {
        this.itemId = itemId;
        this.count = count;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    @Override
    public QuestRewardType<?> getType() {
        return QuestRewardType.ITEM;
    }

    @Override
    public Component getDisplayComponent() {
        String text = displayText != null ? displayText :
                String.format("%s x%d", itemId.getPath(), count);
        return Component.literal(text);
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText :
                String.format("%s x%d", itemId.getPath(), count);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "item");
        json.addProperty("item_id", itemId.toString());
        json.addProperty("count", count);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeResourceLocation(itemId);
        buf.writeInt(count);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static ItemReward fromJson(JsonObject json) {
        ResourceLocation itemId = new ResourceLocation(json.get("item_id").getAsString());
        int count = json.get("count").getAsInt();
        String display = QuestDisplayUtil.readDisplay(json);
        return new ItemReward(itemId, count, display);
    }

    public static ItemReward fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation itemId = buf.readResourceLocation();
        int count = buf.readInt();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new ItemReward(itemId, count, display);
    }
}
