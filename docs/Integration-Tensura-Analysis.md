Tensura Reincarnated – Integration Analysis for WorldVoice

Summary
- Target: com.github.manasmods.tensura (Curse mod “Tensura Reincarnated”, 1.19.2 parchment-mapped).
- Goal: Integrate a slot-based Unique/Ultimate skill system while remaining compatible with Tensura features and configs.
- Key finding: Reincarnation Unique assignment is driven by RaceSelectionMenu and gated via a global UniqueSkillSaveData + gamerules. No TTL, no multi-holder capacity.

Relevant Artifacts (paths in the deobf jar)
- Reincarnation flow
  - Class: `com/github/manasmods/tensura/menu/RaceSelectionMenu`.
  - Methods:
    - `setRace(Player, Race, boolean resetEP, boolean grantUnique)` → calls `grantUniqueSkill` on submit when not race-only.
    - `grantUniqueSkill(Player)` → if `NO_UNIQUE_START` gamerule is true, grants EP/Aura instead of Unique; else calls `randomUniqueSkill`.
    - `randomUniqueSkill(Player, boolean coverEP)` → picks 1..N unique skills and calls `SkillStorage.learnSkill(...)`, then records owner in UniqueSkillSaveData.
    - `getReincarnationSkills(ServerLevel, boolean ignoreGameRule, @Nullable Player roller)` → filters config list by TRULY_UNIQUE + Existing Owners + “roller already has skill”.
    - `getSecondReincarnationSkills(ServerLevel, boolean ignoreGameRule)` → same filtering for a secondary list.

- Gamerules (accessors in TensuraGameRules)
  - `TRULY_UNIQUE` (boolean) → when true, `getReincarnationSkills` and `getSecondReincarnationSkills` exclude skills present in UniqueSkillSaveData.
  - `NO_UNIQUE_START` (boolean) → when true, on reincarnation players do not receive Unique; instead they receive EP/MP compensation based on `skills.skillNumber` and `MP_SKILL_COST`.
  - Other related: `RESET_COUNTER_BONUS_UNIQUE` (int) and config `skills.maxCounterBonus` and `skills.additionalUniqueChance` modify how many unique picks are rolled on reincarnation.

- Unique ownership store
  - Class: `com/github/manasmods/tensura/world/savedata/UniqueSkillSaveData` (SavedData).
    - Map: `Map<ResourceLocation skill, UUID owner>` (single owner per skill; no timestamps/TTL; no multi-holder).
    - Methods: `addSkill(skillId, uuid)`, `removeSkill(skillId)`, `hasSkill(skillId)`, `getOwner(skillId)`.
    - Storage id: `tensura_unique_skills` in world saved data.
  - Usage examples:
    - On grant in `RaceSelectionMenu.randomUniqueSkill(...)`: after `learnSkill`, records owner via `UniqueSkillSaveData.addSkill(...)`.
    - On reset in `com/github/manasmods/tensura/item/custom/ResetScrollItem.resetEverything(ServerPlayer)`: removes mappings for owned skills when wiping a character.

- Config (server): `defaultconfigs/tensura-reincarnated/common.toml`
  - Section `[skills.reincarnationSkills]` → primary pool of Unique skills (resource IDs) and `skillNumber`, `additionalUniqueChance`, `secondReincarnationSkills`, `maxCounterBonus`, and `counterPenaltyNonCharScroll`.
  - WorldVoice should read this list by default when seeding its pool.

- Skill API usage (ManasCore)
  - Get registry value from id: `SkillAPI.getSkillRegistry().getValue(new ResourceLocation(id))` → returns `ManasSkill`.
  - Check/learn/forget:
    - Check: `SkillAPI.getSkillsFrom(entity).getSkill(manasSkill)` and/or `SkillUtils.hasSkill(entity, manasSkill)`.
    - Learn: `SkillAPI.getSkillsFrom(entity).learnSkill(new TensuraSkillInstance(manasSkill))` (returns boolean). Optionally set `instance.getOrCreateTag().putBoolean("NoMagiculeCost", true)` to waive EP/MP cost when desired.
    - Forget: `SkillAPI.getSkillsFrom(entity).forgetSkill(manasSkill)`.

Critical Issues (with TrulyUnique)
- Single-holder only: `UniqueSkillSaveData` enforces exactly one owner per skill; cannot represent slots > 1 holders.
- No TTL/inactivity: Entries never expire; abandoned owners keep blocking new assignments until manual reset/scroll or admin action.
- No whitelist/bypass: No way to exempt players from uniqueness or auto-expiry.
- Global gating tied to gamerule: `TRULY_UNIQUE` toggles gating, not capacity; `NO_UNIQUE_START` substitutes Unique assignment with EP/MP rewards.

Integration Options
1) Mixin Override (recommended)
   - Intercept `RaceSelectionMenu.grantUniqueSkill(Player)` and `RaceSelectionMenu.randomUniqueSkill(Player, boolean)` with a Mixin to delegate to WorldVoice’s `SkillService`.
   - Behavior:
     - If WorldVoice is active, cancel original method and invoke WV’s slot/capacity logic for Unique assignment using WV’s pools and TTL.
     - Respect `NO_UNIQUE_START`: when true, mirror Tensura’s EP/Aura compensation path or call original.
     - Write to `UniqueSkillSaveData` only if keeping Tensura gating enabled for backward compatibility; otherwise, keep `TRULY_UNIQUE=false` and let WV fully govern capacity.
   - Signature targets:
     - `Lcom/github/manasmods/tensura/menu/RaceSelectionMenu;grantUniqueSkill(Lnet/minecraft/world/entity/player/Player;)V`
     - `Lcom/github/manasmods/tensura/menu/RaceSelectionMenu;randomUniqueSkill(Lnet/minecraft/world/entity/player/Player;Z)V`

2) Gamerule-Based Disable + WV Assignment (no Mixin)
   - Set `NO_UNIQUE_START=true` to stop Tensura from handing out Unique on reincarnation; WV then assigns Unique via its own triggers (e.g., on menu submit proxy, on first login post-reincarnation, or via an admin command/workflow).
   - Downsides: No direct server signal of “reincarnation finished” without patching; WV may need to infer from player state changes or race capability updates.

3) TTL-Only Cleanup on Tensura State (partial)
   - Maintain WV’s last-seen ledger and periodically remove stale entries from `UniqueSkillSaveData` to unblock `TRULY_UNIQUE` gating after inactivity.
   - Limitations: Still single-owner; cannot implement per-skill multi-slot capacity; revoked owners keep the skill until next login.

Adapter Surface (WV -> Tensura)
- Resolve ids: `ResourceLocation id = new ResourceLocation("tensura:great_sage"); ManasSkill s = SkillAPI.getSkillRegistry().getValue(id);`
- Grant: `SkillStorage st = SkillAPI.getSkillsFrom(player); TensuraSkillInstance inst = new TensuraSkillInstance(s); inst.getOrCreateTag().putBoolean("NoMagiculeCost", true); boolean ok = st.learnSkill(inst);`
- Revoke: `SkillAPI.getSkillsFrom(player).forgetSkill(s);`
- Check: `SkillUtils.hasSkill(player, s)` or `st.getSkill(s).isPresent()`
- Seed pools: parse `config/tensura-reincarnated/common.toml` -> `[skills.reincarnationSkills.reincarnationSkills]` list; optionally also `secondReincarnationSkills`.
- Gating compatibility:
  - If leaving `TRULY_UNIQUE=true`, mirror writes to `UniqueSkillSaveData.addSkill/removeSkill` to match WV’s slot occupancy (only supports capacity=1; not suitable for multi-slot).
  - If setting `TRULY_UNIQUE=false`, avoid writes and let WV fully control availability.

Where to Hook
- Primary: RaceSelectionMenu static methods above. Mixin injection at `@At("HEAD")` with `cancellable = true` recommended; invoke WV service server-side only.
- Secondary: Optional hook into `ResetScrollItem.resetEverything(ServerPlayer)` to observe/remap legacy uniqueness on character reset (for migration or audit only).

Observations / Design Implications
- WV Capacity: Because `UniqueSkillSaveData` can only store one owner, WV must either (a) disable Tensura uniqueness gating (`TRULY_UNIQUE=false`) and enforce capacity itself, or (b) maintain a parallel ledger and ignore Tensura gating entirely.
- TTL Revoke: For offline revokes, WV should (1) free the slot immediately in its ledger, (2) optionally remove tensura mapping if it remains enabled, and (3) queue a pending revoke to run on next login.
- EP/MP Compensation: If servers rely on `NO_UNIQUE_START`, WV should respect that path and not grant Unique unless admins explicitly opt-in.
- Randomness Parity: WV can match Tensura’s random selection semantics for fairness, including bonus rolls via reset counter logic if desired; this logic lives currently in `RaceSelectionMenu.randomUniqueSkill` and is easily replicated.

Risks & Caveats
- Mixin fragility: Upstream updates to method signatures or class names could break injection. Use @Slice/@At signatures conservatively and log failures.
- Cross-mod latency: Skill grant/revoke should happen server-side; ensure client state stays in sync (ManasCore typically syncs on `learnSkill`/`forgetSkill`).
- Duplicate owners: With `TRULY_UNIQUE=false`, if WV is disabled or misconfigured, Tensura will allow duplicates on reincarnation. Ensure WV is active before disabling gating.

Recommendations
- Adopt Mixin override for `RaceSelectionMenu.grantUniqueSkill` and `randomUniqueSkill` to centralize Unique assignment in WV.
- Set `TRULY_UNIQUE=false` to avoid interference once WV is active; optionally auto-enforce this gamerule on server start (configurable).
- Seed WV pools from `config/tensura-reincarnated/common.toml` and provide a reload command.
- Implement a migration that reads existing `tensura_unique_skills` (UniqueSkillSaveData) and populates WV’s ledger; optionally clear tensura mappings afterward if WV becomes the authority.
- Provide explicit `grant/revoke` commands utilizing ManasCore API (`learnSkill`/`forgetSkill`) and optional writes to UniqueSkillSaveData when compatibility mode is on.

Concrete Code References (for quick lookup)
- RaceSelectionMenu.java: setRace → 120–200, grantUniqueSkill → 180–194, randomUniqueSkill → 194–220, getReincarnationSkills → 220–237, getSecondReincarnationSkills → 237–244.
- UniqueSkillSaveData.java: in-memory map + SavedData plumbing, used on grant/reset.
- TensuraGameRules.java: gamerule keys include TRULY_UNIQUE and NO_UNIQUE_START; helper methods exist for some gamerules.
- ResetScrollItem.java: resetEverything(ServerPlayer) clears skills and removes `UniqueSkillSaveData` entries for owned skills.

Next Steps
- Choose integration mode (Mixin override recommended) and confirm server policy for `TRULY_UNIQUE` and `NO_UNIQUE_START`.
- Implement WV’s TensuraAdapter with grant/revoke/has/resolve methods and pool seeding.
- Implement Mixin to intercept RaceSelectionMenu (server-side only) and delegate to WV SkillService.
- Add migration command to import owners from `UniqueSkillSaveData`.

