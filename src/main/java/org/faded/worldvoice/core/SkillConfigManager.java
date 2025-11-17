package org.faded.worldvoice.core;

import com.github.manasmods.manascore.api.skills.ManasSkill;
import com.github.manasmods.manascore.api.skills.SkillAPI;
import com.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.faded.worldvoice.config.WVServerConfig;
import org.faded.worldvoice.util.LogJsonL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SkillConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillConfigManager.class);

    public static final SkillConfigManager INSTANCE = new SkillConfigManager();

    private final Map<ResourceLocation, Integer> uniqueSlots = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> ultimateSlots = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> manualSlots = new LinkedHashMap<>();

    private boolean blUniqueAll = false;
    private boolean blUltimateAll = false;
    private final java.util.LinkedHashSet<ResourceLocation> blSkills = new java.util.LinkedHashSet<>();

    private boolean plUniqueAll = false;
    private boolean plUltimateAll = false;
    private final java.util.LinkedHashSet<ResourceLocation> plSkills = new java.util.LinkedHashSet<>();

    private SkillConfigManager() {}

    public void reload() {
        uniqueSlots.clear();
        ultimateSlots.clear();
        manualSlots.clear();

        parseEntries(WVServerConfig.UNIQUE_SLOTS.get(), SkillCategory.UNIQUE, uniqueSlots);
        parseEntries(WVServerConfig.ULTIMATE_SLOTS.get(), SkillCategory.ULTIMATE, ultimateSlots);
        parseEntries(WVServerConfig.MANUAL_SLOTS.get(), null, manualSlots);

        if (WVServerConfig.AUTO_ADD_NEW_SKILLS.get()) {
            autoAddDiscoveredSkills();
        }

        syncConfigLists();

        blUniqueAll = WVServerConfig.BL_UNIQUE_ALL.get();
        blUltimateAll = WVServerConfig.BL_ULTIMATE_ALL.get();
        blSkills.clear();
        importIdList(WVServerConfig.BL_SKILLS.get(), blSkills);

        plUniqueAll = WVServerConfig.PL_UNIQUE_ALL.get();
        plUltimateAll = WVServerConfig.PL_ULTIMATE_ALL.get();
        plSkills.clear();
        importIdList(WVServerConfig.PL_SKILLS.get(), plSkills);
    }

    private void autoAddDiscoveredSkills() {
        for (ManasSkill skill : SkillAPI.getSkillRegistry().getValues()) {
            ResourceLocation id = skill.getRegistryName();
            if (id == null) continue;
            SkillCategory cat = detectCategory(skill);
            if (cat == SkillCategory.UNIQUE) {
                uniqueSlots.putIfAbsent(id, org.faded.worldvoice.config.SkillConfigDefaults.DEFAULT_CAPACITY);
            } else if (cat == SkillCategory.ULTIMATE) {
                ultimateSlots.putIfAbsent(id, org.faded.worldvoice.config.SkillConfigDefaults.DEFAULT_CAPACITY);
            }
        }
    }

    private void importIdList(List<? extends String> src, java.util.Set<ResourceLocation> out) {
        if (src == null) return;
        for (String s : src) {
            if (s == null || s.isBlank()) continue;
            ResourceLocation id = ResourceLocation.tryParse(s.trim());
            if (id != null) out.add(id);
        }
    }

    private static List<String> toSlotList(Map<ResourceLocation, Integer> map) {
        List<String> out = new ArrayList<>(map.size());
        for (Map.Entry<ResourceLocation, Integer> e : map.entrySet()) {
            out.add(e.getKey().toString() + "=" + e.getValue());
        }
        out.sort(String::compareTo);
        return out;
    }

    public void syncConfigLists() {
        try {
            WVServerConfig.UNIQUE_SLOTS.set(toSlotList(uniqueSlots));
            WVServerConfig.ULTIMATE_SLOTS.set(toSlotList(ultimateSlots));
            WVServerConfig.BL_SKILLS.set(new java.util.ArrayList<>(blSkills.stream().map(ResourceLocation::toString).toList()));
            WVServerConfig.PL_SKILLS.set(new java.util.ArrayList<>(plSkills.stream().map(ResourceLocation::toString).toList()));
            WVServerConfig.saveIfPossible();
        } catch (Throwable t) {
            LOGGER.warn("Failed to save updated WorldVoice server config lists: {}", t.toString());
        }
    }

    private void parseEntries(List<? extends String> rawEntries, SkillCategory forcedCategory, Map<ResourceLocation, Integer> out) {
        if (rawEntries == null) return;
        for (String entry : rawEntries) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.split("=");
            if (parts.length < 2) {
                LOGGER.warn("Invalid slot entry '{}', expected namespace:path=slots", entry);
                continue;
            }
            ResourceLocation id;
            try {
                id = ResourceLocation.tryParse(parts[0].trim());
            } catch (Exception e) {
                LOGGER.warn("Invalid skill id '{}': {}", parts[0], e.toString());
                continue;
            }
            if (id == null) {
                LOGGER.warn("Invalid skill id '{}': null", parts[0]);
                continue;
            }
            int slots;
            try {
                slots = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ex) {
                LOGGER.warn("Invalid slot count '{}' for {}", parts[1], id);
                continue;
            }
            if (slots < 0) slots = 0;
            if (forcedCategory == SkillCategory.UNIQUE && detectCategory(id) == SkillCategory.ULTIMATE) {
                LOGGER.warn("Skill {} detected as ultimate but listed under uniqueSlots; keeping as unique per config", id);
            }
            out.put(id, slots);
        }
    }

    public void applyToLedger(ServerLevel level) {
        SlotLedger ledger = SlotLedger.get(level);
        for (Map.Entry<ResourceLocation, Integer> entry : uniqueSlots.entrySet()) {
            var state = ledger.getOrCreateSkill(SkillCategory.UNIQUE, entry.getKey());
            if (state.capacity != entry.getValue()) {
                state.capacity = entry.getValue();
                state.epoch++;
            }
        }
        for (Map.Entry<ResourceLocation, Integer> entry : ultimateSlots.entrySet()) {
            var state = ledger.getOrCreateSkill(SkillCategory.ULTIMATE, entry.getKey());
            if (state.capacity != entry.getValue()) {
                state.capacity = entry.getValue();
                state.epoch++;
            }
        }
        for (Map.Entry<ResourceLocation, Integer> entry : manualSlots.entrySet()) {
            SkillCategory cat = detectCategory(entry.getKey());
            if (cat == null) cat = SkillCategory.UNIQUE;
            var state = ledger.getOrCreateSkill(cat, entry.getKey());
            if (state.capacity != entry.getValue()) {
                state.capacity = entry.getValue();
                state.epoch++;
            }
        }
        ledger.setDirty();
        LogJsonL.append(String.format("{\"ts\":\"%s\",\"event\":\"config-apply\",\"unique\":%d,\"ultimate\":%d}",
                LogJsonL.iso8601(System.currentTimeMillis()), uniqueSlots.size(), ultimateSlots.size()));
    }

    public SkillCategory classify(ResourceLocation id) {
        if (uniqueSlots.containsKey(id)) return SkillCategory.UNIQUE;
        if (ultimateSlots.containsKey(id)) return SkillCategory.ULTIMATE;
        if (manualSlots.containsKey(id)) {
            SkillCategory cat = detectCategory(id);
            return cat != null ? cat : SkillCategory.UNIQUE;
        }
        ManasSkill skill = SkillAPI.getSkillRegistry().getValue(id);
        if (skill == null) return null;
        return detectCategory(skill);
    }

    private SkillCategory detectCategory(ResourceLocation id) {
        ManasSkill skill = SkillAPI.getSkillRegistry().getValue(id);
        if (skill == null) return null;
        return detectCategory(skill);
    }

    private SkillCategory detectCategory(ManasSkill skill) {
        if (skill instanceof Skill tensuraSkill) {
            Skill.SkillType type = tensuraSkill.getType();
            if (type == Skill.SkillType.UNIQUE) {
                return SkillCategory.UNIQUE;
            }
            if (type == Skill.SkillType.ULTIMATE) {
                return SkillCategory.ULTIMATE;
            }
        }
        String className = skill.getClass().getName().toLowerCase(Locale.ROOT);
        if (className.contains(".skill.unique.") || className.contains(".ability.unique.")) {
            return SkillCategory.UNIQUE;
        }
        if (className.contains(".skill.ultimate.") || className.contains(".ability.ultimate.")) {
            return SkillCategory.ULTIMATE;
        }
        ResourceLocation id = skill.getRegistryName();
        if (id != null) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("ultimate")) return SkillCategory.ULTIMATE;
            if (path.contains("unique")) return SkillCategory.UNIQUE;
        }
        return null;
    }

    public Collection<ResourceLocation> getManagedSkills() {
        Set<ResourceLocation> all = new LinkedHashSet<>();
        all.addAll(uniqueSlots.keySet());
        all.addAll(ultimateSlots.keySet());
        all.addAll(manualSlots.keySet());
        return all;
    }

    public int getCapacity(SkillCategory cat, ResourceLocation id) {
        Map<ResourceLocation, Integer> map = cat == SkillCategory.UNIQUE ? uniqueSlots : ultimateSlots;
        if (map.containsKey(id)) return map.get(id);
        if (manualSlots.containsKey(id)) return manualSlots.get(id);
        return org.faded.worldvoice.config.SkillConfigDefaults.DEFAULT_CAPACITY;
    }

    public SkillCategory setCapacity(ResourceLocation id, int capacity) {
        SkillCategory cat = classify(id);
        if (cat == null) {
            cat = detectCategory(id);
            if (cat == null) {
                cat = SkillCategory.UNIQUE;
            }
        }
        if (cat == SkillCategory.UNIQUE) {
            uniqueSlots.put(id, capacity);
        } else if (cat == SkillCategory.ULTIMATE) {
            ultimateSlots.put(id, capacity);
        } else {
            manualSlots.put(id, capacity);
        }
        syncConfigLists();
        return cat;
    }

    public boolean isLearningBlacklisted(SkillCategory cat, ResourceLocation id) {
        if (id == null) return false;
        if (blSkills.contains(id)) return true;
        if (cat == SkillCategory.UNIQUE && blUniqueAll) return true;
        if (cat == SkillCategory.ULTIMATE && blUltimateAll) return true;
        return false;
    }

    public void setBlacklistCategory(SkillCategory cat, boolean value) {
        if (cat == SkillCategory.UNIQUE) blUniqueAll = value; else if (cat == SkillCategory.ULTIMATE) blUltimateAll = value;
        WVServerConfig.BL_UNIQUE_ALL.set(blUniqueAll);
        WVServerConfig.BL_ULTIMATE_ALL.set(blUltimateAll);
        WVServerConfig.saveIfPossible();
    }

    public boolean addBlacklistedSkill(ResourceLocation id) {
        boolean changed = blSkills.add(id);
        if (changed) syncConfigLists();
        return changed;
    }

    public boolean removeBlacklistedSkill(ResourceLocation id) {
        boolean changed = blSkills.remove(id);
        if (changed) syncConfigLists();
        return changed;
    }

    public boolean isBlacklistUniqueAll() {
        return blUniqueAll;
    }

    public boolean isBlacklistUltimateAll() {
        return blUltimateAll;
    }

    public Set<ResourceLocation> getBlacklistedSkills() {
        return Collections.unmodifiableSet(blSkills);
    }

    public boolean isPlunderRestricted(SkillCategory cat, ResourceLocation id) {
        if (id != null && plSkills.contains(id)) return true;
        if (cat == SkillCategory.UNIQUE && plUniqueAll) return true;
        if (cat == SkillCategory.ULTIMATE && plUltimateAll) return true;
        return false;
    }

    public void setPlunderCategory(SkillCategory cat, boolean value) {
        if (cat == SkillCategory.UNIQUE) plUniqueAll = value; else if (cat == SkillCategory.ULTIMATE) plUltimateAll = value;
        WVServerConfig.PL_UNIQUE_ALL.set(plUniqueAll);
        WVServerConfig.PL_ULTIMATE_ALL.set(plUltimateAll);
        WVServerConfig.saveIfPossible();
    }

    public boolean addPlunderRestrictedSkill(ResourceLocation id) {
        boolean changed = plSkills.add(id);
        if (changed) syncConfigLists();
        return changed;
    }

    public boolean removePlunderRestrictedSkill(ResourceLocation id) {
        boolean changed = plSkills.remove(id);
        if (changed) syncConfigLists();
        return changed;
    }

    public java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSkills(com.mojang.brigadier.context.CommandContext<?> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (ResourceLocation id : getManagedSkills()) {
            builder.suggest(id.toString());
        }
        return builder.buildFuture();
    }
}
