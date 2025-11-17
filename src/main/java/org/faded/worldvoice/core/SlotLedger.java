package org.faded.worldvoice.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class SlotLedger extends SavedData {
    public static final String STORAGE_KEY = "worldvoice_slots";

    public int version = 1;

    public final Settings settings = new Settings();

    public final EnumMap<SkillCategory, Map<ResourceLocation, SkillState>> categories = new EnumMap<>(SkillCategory.class);

    public final Map<UUID, PlayerRecord> players = new HashMap<>();

    public SlotLedger() {
        for (SkillCategory cat : SkillCategory.values()) {
            categories.put(cat, new HashMap<>());
        }
    }

    public static SlotLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                nbt -> fromNbt(nbt), SlotLedger::new, STORAGE_KEY
        );
    }

    public static SlotLedger fromNbt(CompoundTag tag) {
        SlotLedger l = new SlotLedger();
        l.version = tag.getInt("version");
        if (tag.contains("settings", Tag.TAG_COMPOUND)) {
            l.settings.fromNbt(tag.getCompound("settings"));
        }
        CompoundTag cats = tag.getCompound("categories");
        for (SkillCategory cat : SkillCategory.values()) {
            if (cats.contains(cat.name(), Tag.TAG_LIST)) {
                ListTag list = cats.getList(cat.name(), Tag.TAG_COMPOUND);
                for (Tag t : list) {
                    CompoundTag e = (CompoundTag) t;
                    ResourceLocation id = new ResourceLocation(e.getString("id"));
                    SkillState state = new SkillState();
                    state.fromNbt(e.getCompound("state"));
                    l.categories.get(cat).put(id, state);
                }
            }
        }
        if (tag.contains("players", Tag.TAG_LIST)) {
            ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                CompoundTag e = (CompoundTag) t;
                UUID uuid = e.getUUID("id");
                PlayerRecord pr = new PlayerRecord();
                pr.fromNbt(e.getCompound("data"));
                l.players.put(uuid, pr);
            }
        }
        return l;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", version);
        tag.put("settings", settings.toNbt());
        CompoundTag cats = new CompoundTag();
        for (Map.Entry<SkillCategory, Map<ResourceLocation, SkillState>> ent : categories.entrySet()) {
            ListTag list = new ListTag();
            for (Map.Entry<ResourceLocation, SkillState> e : ent.getValue().entrySet()) {
                CompoundTag n = new CompoundTag();
                n.putString("id", e.getKey().toString());
                n.put("state", e.getValue().toNbt());
                list.add(n);
            }
            cats.put(ent.getKey().name(), list);
        }
        tag.put("categories", cats);
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, PlayerRecord> e : players.entrySet()) {
            CompoundTag n = new CompoundTag();
            n.putUUID("id", e.getKey());
            n.put("data", e.getValue().toNbt());
            playersTag.add(n);
        }
        tag.put("players", playersTag);
        return tag;
    }

    public PlayerRecord getOrCreatePlayer(UUID id, String lastKnownName) {
        PlayerRecord pr = players.computeIfAbsent(id, k -> new PlayerRecord());
        if (lastKnownName != null && !lastKnownName.isEmpty()) pr.lastKnownName = lastKnownName;
        return pr;
    }

    public SkillState getOrCreateSkill(SkillCategory cat, ResourceLocation id) {
        return categories.get(cat).computeIfAbsent(id, k -> new SkillState());
    }

    public static class Settings {
        public int ttlDays = 14;
        public int refreshIntervalMinutes = 60;

        public CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putInt("ttlDays", ttlDays);
            t.putInt("refreshIntervalMinutes", refreshIntervalMinutes);
            return t;
        }

        public void fromNbt(CompoundTag t) {
            ttlDays = t.getInt("ttlDays");
            refreshIntervalMinutes = t.getInt("refreshIntervalMinutes");
        }
    }

    public static class PlayerRecord {
        public String lastKnownName = "";
        public long lastSeenMs = 0L;
        public boolean refreshExempt = false;
        public boolean bypassAll = false;
        public final EnumSet<SkillCategory> bypassCategories = EnumSet.noneOf(SkillCategory.class);
        public final LinkedHashSet<ResourceLocation> bypassSkills = new LinkedHashSet<>();

        public boolean blacklistBypassAll = false;
        public final EnumSet<SkillCategory> blacklistBypassCategories = EnumSet.noneOf(SkillCategory.class);
        public final LinkedHashSet<ResourceLocation> blacklistBypassSkills = new LinkedHashSet<>();

        public CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putString("name", lastKnownName);
            t.putLong("lastSeenMs", lastSeenMs);
            t.putBoolean("refreshExempt", refreshExempt);
            t.putBoolean("bypassAll", bypassAll);
            t.putBoolean("bypassSlots", bypassAll);
            t.putBoolean("blacklistBypassAll", blacklistBypassAll);
            ListTag categoriesTag = new ListTag();
            for (SkillCategory cat : bypassCategories) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", cat.name());
                categoriesTag.add(entry);
            }
            t.put("bypassCategories", categoriesTag);
            ListTag skillsTag = new ListTag();
            for (ResourceLocation id : bypassSkills) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id.toString());
                skillsTag.add(entry);
            }
            t.put("bypassSkills", skillsTag);
            ListTag blcCat = new ListTag();
            for (SkillCategory cat : blacklistBypassCategories) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", cat.name());
                blcCat.add(entry);
            }
            t.put("blacklistBypassCategories", blcCat);
            ListTag blcSkills = new ListTag();
            for (ResourceLocation id : blacklistBypassSkills) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id.toString());
                blcSkills.add(entry);
            }
            t.put("blacklistBypassSkills", blcSkills);
            return t;
        }

        public void fromNbt(CompoundTag t) {
            lastKnownName = t.getString("name");
            lastSeenMs = t.getLong("lastSeenMs");
            refreshExempt = t.getBoolean("refreshExempt");
            bypassAll = t.getBoolean("bypassAll") || t.getBoolean("bypassSlots");
            blacklistBypassAll = t.getBoolean("blacklistBypassAll");
            bypassCategories.clear();
            if (t.contains("bypassCategories", Tag.TAG_LIST)) {
                ListTag categoriesTag = t.getList("bypassCategories", Tag.TAG_COMPOUND);
                for (Tag tag : categoriesTag) {
                    String id = ((CompoundTag) tag).getString("id");
                    try {
                        bypassCategories.add(SkillCategory.valueOf(id));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            bypassSkills.clear();
            if (t.contains("bypassSkills", Tag.TAG_LIST)) {
                ListTag skillsTag = t.getList("bypassSkills", Tag.TAG_COMPOUND);
                for (Tag tag : skillsTag) {
                    ResourceLocation id = ResourceLocation.tryParse(((CompoundTag) tag).getString("id"));
                    if (id != null) {
                        bypassSkills.add(id);
                    }
                }
            }
            blacklistBypassCategories.clear();
            if (t.contains("blacklistBypassCategories", Tag.TAG_LIST)) {
                ListTag blc = t.getList("blacklistBypassCategories", Tag.TAG_COMPOUND);
                for (Tag tag : blc) {
                    String id = ((CompoundTag) tag).getString("id");
                    try {
                        blacklistBypassCategories.add(SkillCategory.valueOf(id));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            blacklistBypassSkills.clear();
            if (t.contains("blacklistBypassSkills", Tag.TAG_LIST)) {
                ListTag bls = t.getList("blacklistBypassSkills", Tag.TAG_COMPOUND);
                for (Tag tag : bls) {
                    ResourceLocation id = ResourceLocation.tryParse(((CompoundTag) tag).getString("id"));
                    if (id != null) blacklistBypassSkills.add(id);
                }
            }
        }

        public boolean hasBypass(SkillCategory category, ResourceLocation id) {
            if (bypassAll) return true;
            if (category != null && bypassCategories.contains(category)) return true;
            return id != null && bypassSkills.contains(id);
        }

        public boolean hasBlacklistBypass(SkillCategory category, ResourceLocation id) {
            if (blacklistBypassAll) return true;
            if (category != null && blacklistBypassCategories.contains(category)) return true;
            return id != null && blacklistBypassSkills.contains(id);
        }

        public void addBypassAll() {
            bypassAll = true;
        }

        public void removeBypassAll() {
            bypassAll = false;
        }

        public void addBypassCategory(SkillCategory category) {
            if (category != null) {
                bypassCategories.add(category);
            }
        }

        public void removeBypassCategory(SkillCategory category) {
            if (category != null) {
                bypassCategories.remove(category);
            }
        }

        public void addBypassSkill(ResourceLocation id) {
            if (id != null) {
                bypassSkills.add(id);
            }
        }

        public void removeBypassSkill(ResourceLocation id) {
            if (id != null) {
                bypassSkills.remove(id);
            }
        }

        public void addBlacklistBypassAll() { blacklistBypassAll = true; }
        public void removeBlacklistBypassAll() { blacklistBypassAll = false; }
        public void addBlacklistBypassCategory(SkillCategory category) { if (category != null) blacklistBypassCategories.add(category); }
        public void removeBlacklistBypassCategory(SkillCategory category) { if (category != null) blacklistBypassCategories.remove(category); }
        public void addBlacklistBypassSkill(ResourceLocation id) { if (id != null) blacklistBypassSkills.add(id); }
        public void removeBlacklistBypassSkill(ResourceLocation id) { if (id != null) blacklistBypassSkills.remove(id); }
    }

    public static class SkillState {
        public int capacity = 1;
        public final LinkedHashSet<UUID> holders = new LinkedHashSet<>();
        public final LinkedList<UUID> waitlist = new LinkedList<>();
        public int epoch = 0;

        public CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putInt("capacity", capacity);
            t.putInt("epoch", epoch);
            ListTag h = new ListTag();
            for (UUID u : holders) {
                CompoundTag e = new CompoundTag();
                e.putUUID("id", u);
                h.add(e);
            }
            t.put("holders", h);
            ListTag w = new ListTag();
            for (UUID u : waitlist) {
                CompoundTag e = new CompoundTag();
                e.putUUID("id", u);
                w.add(e);
            }
            t.put("waitlist", w);
            return t;
        }

        public void fromNbt(CompoundTag t) {
            capacity = t.getInt("capacity");
            epoch = t.contains("epoch") ? t.getInt("epoch") : 0;
            holders.clear();
            if (t.contains("holders", Tag.TAG_LIST)) {
                ListTag h = t.getList("holders", Tag.TAG_COMPOUND);
                for (Tag tag : h) holders.add(((CompoundTag) tag).getUUID("id"));
            }
            waitlist.clear();
            if (t.contains("waitlist", Tag.TAG_LIST)) {
                ListTag w = t.getList("waitlist", Tag.TAG_COMPOUND);
                for (Tag tag : w) waitlist.add(((CompoundTag) tag).getUUID("id"));
            }
        }
    }
}
