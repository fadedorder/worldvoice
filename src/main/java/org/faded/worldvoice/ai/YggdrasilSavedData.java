package org.faded.worldvoice.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class YggdrasilSavedData extends SavedData {
    public static final String STORAGE_KEY = "worldvoice_yggdrasil";

    private final Map<UUID, PlayerRecord> players = new HashMap<>();

    public static YggdrasilSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                YggdrasilSavedData::load, YggdrasilSavedData::new, STORAGE_KEY
        );
    }

    private static YggdrasilSavedData load(CompoundTag tag) {
        YggdrasilSavedData data = new YggdrasilSavedData();
        if (tag.contains("players", Tag.TAG_LIST)) {
            ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
            for (Tag entry : list) {
                CompoundTag wrapped = (CompoundTag) entry;
                UUID id = wrapped.getUUID("id");
                PlayerRecord record = new PlayerRecord();
                record.lastEvaluationMs = wrapped.getLong("lastEval");
                record.uniqueGranted = wrapped.getInt("uniqueGranted");
                record.lastWishMs = wrapped.getLong("lastWish");
                record.favorUntilMs = wrapped.getLong("favorUntil");
                record.favorStrength = wrapped.getDouble("favorStrength");
                if (record.favorStrength < 0.0d || record.favorStrength > 10.0d) {
                    record.favorStrength = Math.max(0.0d, Math.min(10.0d, record.favorStrength));
                }
                record.favorContext = wrapped.getString("favorContext");
                if (wrapped.contains("uniqueCapOverride")) {
                    record.uniqueCapOverride = wrapped.getInt("uniqueCapOverride");
                }
                data.players.put(id, record);
            }
        }
        return data;
    }

    public PlayerRecord getOrCreate(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerRecord());
    }

    public PlayerRecord get(UUID id) {
        return players.get(id);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerRecord> entry : players.entrySet()) {
            CompoundTag wrapped = new CompoundTag();
            wrapped.putUUID("id", entry.getKey());
            PlayerRecord record = entry.getValue();
            wrapped.putLong("lastEval", record.lastEvaluationMs);
            wrapped.putInt("uniqueGranted", record.uniqueGranted);
            wrapped.putLong("lastWish", record.lastWishMs);
            wrapped.putLong("favorUntil", record.favorUntilMs);
            wrapped.putDouble("favorStrength", record.favorStrength);
            wrapped.putString("favorContext", record.favorContext);
            wrapped.putInt("uniqueCapOverride", record.uniqueCapOverride);
            list.add(wrapped);
        }
        tag.put("players", list);
        return tag;
    }

    public static final class PlayerRecord {
        public long lastEvaluationMs = 0L;
        public int uniqueGranted = 0;
        public long lastWishMs = 0L;
        public long favorUntilMs = 0L;
        public double favorStrength = 0.0d;
        public String favorContext = "";
        public int uniqueCapOverride = -1;

        public boolean hasFavor(long now) {
            return favorUntilMs > now;
        }

        public double favorScore(long now) {
            if (!hasFavor(now)) return 0.0d;
            return Math.max(0.0d, Math.min(10.0d, favorStrength));
        }

        public double favorMultiplier(long now) {
            double score = favorScore(now);
            if (score <= 0.0d) {
                return 1.0d;
            }
            return Math.min(3.0d, 1.0d + (score * 0.05d));
        }
    }
}
