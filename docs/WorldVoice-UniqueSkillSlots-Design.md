WorldVoice – Unique/Ultimate Skill Slot System (Design)

Overview
- Goal: Replace/augment TrulyUnique with a robust, configurable slot-based system for Unique and Ultimate skills.
- Core: Per-skill slot limits, automatic refresh of inactive holders (default 14 days), full audit logging, manual admin tools, whitelist controls, and integration with Tensura’s reincarnation skills (seed from tensura common.toml by default).
- Scope: Server-side Forge implementation (extendable), safe persistence via SavedData, commands for runtime control, optional waitlist and backfill behavior.

Objectives
- Per-skill slots: Each skill has a capacity ("slots").
- Auto refresh: If a holder hasn’t logged in for N days (default 14), revoke and free slot(s).
- Logging: Append-only JSONL of assignments, revocations, expirations.
- Configurability: Slot counts, TTL, pools, seeding from Tensura, optional waitlists, refresh cadence.
- Compatibility: Works for Unique and Ultimate skills; integrates with Tensura’s skills API or through explicit grant/revoke hooks.
- Admin ergonomics: Commands to assign/revoke, list, set slots, manage pools, run refresh, export owners, and control whitelists.

Non-Goals
- Cross-server sync (Bungee/Velocity). Single logical server assumed.
- Rich UI beyond commands (potential future work).
- Guarantee to revoke skills in third-party storage when player is offline (best-effort + pending clean-up on login).

Key Concepts
- SkillCategory: UNIQUE, ULTIMATE.
- SkillId: Resource location string (e.g., tensura:great_sage). Use `ResourceLocation`.
- Slot/CAP: Maximum concurrent holders for a SkillId within a category.
- Holder: Player UUID (store last known name), assignment timestamps, lastSeen, whitelist flags.
- Pool: Configured set of skills eligible for assignment, with capacities.
- TTL: Days of inactivity before auto-revoke; refresh job runs periodically.

Data Model
- SavedData (NBT; authoritative) at `world/data/worldvoice_slots.dat`:
  - version: int
  - clockEpochMs: long (for testability; real time by default)
  - categories: map
    - UNIQUE / ULTIMATE:
      - skills: map[skillId]
        - capacity: int
        - holders: list[uuid]
        - waitlist: list[uuid] (optional feature)
        - historyCount: int
  - players: map[uuid]
    - name: string (last seen)
    - lastSeenMs: long
    - assignments: map[UNIQUE|ULTIMATE] -> list[skillId]
    - flags:
      - bypassSlots: boolean (whitelist ignoring slot caps)
      - refreshExempt: boolean (whitelist from TTL revocation)
  - settings:
    - ttlDays: int (default 14)
    - refreshIntervalMinutes: int (default 60)
    - preferWaitlistOrder: boolean (default true)
    - allowBackfillOnLogin: boolean (default true)
    - seededFromTensura: boolean

- Logs
  - JSONL at `logs/worldvoice/skill-history-YYYYMMDD.jsonl` entries with fields:
    - ts, category, event(assign|revoke|expire|import|migrate), skillId, playerUUID, playerName, byUser(optional), reason(optional), metadata(optional)
  - Optional structured ledger per skill at `world/worldvoice/history/<category>/<skillId>.jsonl` (future opt-in).

Configuration (ForgeConfigSpec; config/worldvoice-common.toml)
- general:
  - seedFromTensura = true
  - tensuraConfigPath = "config/tensura/common.toml"  # best-effort autodetect
  - defaultInactiveDaysToExpire = 14
  - refreshCheckIntervalMinutes = 60
  - preferWaitlistOrder = true
  - allowBackfillOnLogin = true
  - assignOnReincarnation = true
  - assignOnFirstJoin = false

- unique:
  - slots = { "tensura:great_sage" = 2, "tensura:predator" = 3 }  # example
  - poolInclude = [ ]  # optional explicit include overrides Tensura seeding
  - poolExclude = [ ]  # remove skills from seeded pool
  - whitelistBypassSlots = [ ]  # UUIDs not counted against caps
  - whitelistRefreshExempt = [ ]  # UUIDs not auto-revoked

- ultimate:
  - slots = { }
  - poolInclude = [ ]
  - poolExclude = [ ]

Example Snippet (worldvoice-common.toml)
```
[general]
seedFromTensura = true
tensuraConfigPath = "config/tensura/common.toml"
defaultInactiveDaysToExpire = 14
refreshCheckIntervalMinutes = 60
preferWaitlistOrder = true
allowBackfillOnLogin = true
assignOnReincarnation = true
assignOnFirstJoin = false

[unique]
slots = { "tensura:great_sage" = 2, "tensura:predator" = 3 }
poolInclude = []
poolExclude = ["tensura:unique_disabled_example"]
whitelistBypassSlots = []
whitelistRefreshExempt = []

[ultimate]
slots = {}
poolInclude = []
poolExclude = []
```

Assignment & Revocation Logic
- Trigger points
  - Reincarnation event (preferred; via Tensura adapter) OR first join if enabled OR explicit admin command.
  - On login: update lastSeen; optionally backfill waiting players or re-apply pending revokes/grants.

- Assign flow
  1) Validate pool contains skill; determine category.
  2) If player.flags.bypassSlots -> grant even if capacity reached, do not consume capacity.
  3) Else if holders.size < capacity -> grant and record.
  4) Else add to waitlist (if enabled) and notify.
  5) Persist + log event.

- Revoke flow
  1) Determine reason (manual, expire, migrate, admin).
  2) Revoke via adapter (best-effort; queue if offline if adapter can’t offline revoke).
  3) Update holders, free capacity, and optionally promote next from waitlist.
  4) Persist + log event.

- Refresh job
  - Runs at server start and every `refreshCheckIntervalMinutes`.
  - For each holder not refreshExempt and (now - lastSeenMs) > ttl:
    - Revoke with reason=expire.
    - Backfill from waitlist if available; else capacity remains open.

Tensura Integration
- Adapter (`TensuraAdapter` interface):
  - hasSkill(player, skillId, category)
  - grantSkill(player, skillId, category)
  - revokeSkill(player, skillId, category)
  - enumerateDefaultReincarnationSkills(category): list[skillId] (reads tensura common.toml if present)
  - registerHooks(): subscribe to Reincarnation/Skill events if provided.
- Usage
  - On first run, if `seedFromTensura`, populate pool with the reincarnation skills from Tensura config (filtered by poolExclude + merged with poolInclude).
  - Recommend disabling TrulyUnique gamerule to avoid conflicts; WorldVoice becomes the single source for unique/ultimate distribution.

Commands (Brigadier)
- Base: `/wv` (permission level 3 by default; integrate with Forge permissions if available)
- skill
  - assign <player> <skillId> [unique|ultimate] [--force] [--bypass-slot]
  - revoke <player> <skillId|all> [--reason <text>]
  - list [unique|ultimate] [--holders]
  - info <skillId>
  - holders <skillId>
  - mine  # shows caller’s assignments
- slots
  - set <skillId> <n> [unique|ultimate]
  - get <skillId> [unique|ultimate]
  - open [unique|ultimate]
  - refill <skillId>
- pool
  - add <skillId> [--category unique|ultimate] [--capacity n]
  - remove <skillId> [--category]
  - reload [--dry-run]  # re-read WV and Tensura configs
- whitelist
  - slots add|remove <player>
  - refresh add|remove <player>
  - list
- refresh
  - run [--dry-run]
  - set-ttl <days>
  - set-interval <minutes>
- export
  - owners [unique|ultimate] [--format csv|json]  -> writes to `world/worldvoice/exports`
- migrate
  - from-trulyunique [--dry-run]

Packages & Classes
- org.faded.worldvoice.Worldvoice (existing main)
- org.faded.worldvoice.config
  - WVConfig (ForgeConfigSpec Common)
- org.faded.worldvoice.core
  - SkillCategory (enum)
  - SlotLedger extends SavedData (persist skills/players/state)
  - PlayerRecord, SkillRecord (POJOs)
  - Clock (abstraction for testable time)
- org.faded.worldvoice.service
  - SkillService (assign/revoke/backfill logic)
  - RefreshService (scheduled TTL checks)
  - ExportService (CSV/JSON owners export)
  - MigrationService (TrulyUnique import)
- org.faded.worldvoice.integrations
  - TensuraAdapter (interface)
  - TensuraAdapterForge (impl; config parsing + event hooks)
- org.faded.worldvoice.command
  - CommandRegistrar (registers `/wv` tree)
  - Subcommand handlers (SkillCommands, SlotCommands, PoolCommands, WhitelistCommands, RefreshCommands, ExportCommands)
- org.faded.worldvoice.events
  - CommonEvents (ServerStarting, PlayerLoggedInEvent, etc.)
- org.faded.worldvoice.util
  - LogJsonL (append-only JSONL logger)
  - Ids (ResourceLocation helpers)

Lifecycle & Events
- Server start: load SlotLedger; run refresh pass; if seedFromTensura && not seeded, build pool.
- Player login: update lastSeen; apply pending revoke/grant; optional backfill if capacity exists.
- Server tick: lightweight timer to trigger periodic refresh; avoid heavy work every tick.
- Config reload: apply new slot counts/pools; reconcile capacity and optionally run backfill (with warnings when reducing capacity below current holders).

Edge Cases & Rules
- Reducing capacity below current holders: Do not auto-revoke; flag as over-capacity and backfill paused until count <= capacity. Provide admin command to reconcile (e.g., `wv slots set` with `--enforce` to revoke oldest/least-active).
- Whitelist changes: Removing whitelist doesn’t revoke; it merely makes the player subject to future limits and TTL.
- Unknown/missing skills at runtime: keep state; adapter may no-op; show warnings on info/list/refresh.
- Name changes: state keys are UUID; last known name is stored for human-readable logs.

Testing Plan
- Unit tests (SkillService, RefreshService, SlotLedger serialization, whitelist behavior, waitlist ordering).
- Adapter tests with a mock TensuraAdapter.
- Command unit tests for parsing/routing.
- Manual test matrix: assign/revoke, TTL expiry, capacity changes, whitelist, seed from tensura, server restarts, export/migrate.

Development Plan & Milestones
1) M0 – Foundations
   - WVConfig (common.toml), SavedData (SlotLedger), JSONL logger, base packages, clock abstraction.
   - Commands skeleton: `/wv help`, basic `/wv skill list`.

2) M1 – Slot Ledger & Manual Control
   - Implement SkillService (assign/revoke + persistence + logging).
   - Commands: assign, revoke, info, holders, slots get/set.

3) M2 – Refresh & TTL
   - RefreshService with periodic checks; login lastSeen updates; expire logic.
   - Commands: refresh run, set-ttl, set-interval.

4) M3 – Tensura Integration
   - TensuraAdapterForge: config parse, grant/revoke/hasSkill, optional reincarnation hook.
   - Pool seeding from Tensura common.toml; pool reload command.

5) M4 – Ultimate Skills Parity
   - Mirror features for ULTIMATE category; shared code via SkillCategory.

6) M5 – Whitelists & Waitlist
   - Implement bypassSlots and refreshExempt; waitlist flow; backfill on free slots.
   - Commands: whitelist and pool management.

7) M6 – Exports & Migration
   - Export owners (CSV/JSON); attempt TrulyUnique migration/import (best-effort).

8) M7 – Polish & Docs
   - Validation, warnings, help text, readme; optional data repair commands.

Risks & Mitigations
- Third-party API differences: Use adapter abstraction; fall back to manual commands if events aren’t available.
- Offline revoke limitation: Queue reapplication on login; log clearly when remote state can’t be changed offline.
- Config drift: Provide dry-run for reload; don’t auto-enforce on capacity reductions.

Open Questions
- Precise Tensura config path and field names (confirm exact file for seeding).
- Should whitelist also grant refresh exemption by default or remain separate flags? (Current design: separate.)
- Waitlist policy: strictly FIFO vs weighted random vs priority by playtime? (Default FIFO; make pluggable later.)

Next Steps
- Confirm Tensura config path and the expectation around TrulyUnique gamerule (disable it or let WV override?).
- Implement M0–M2; provide sample config and initial commands for early feedback.

