# Dire Difficulty — design (iteration 1)

**Status:** DESIGN ONLY. Nothing built. This document is the spec that iteration 1 implements.
**Target:** 26.2 Fabric, single node, architected for the same seven-target Stonecutter port the
other two mods already carry.
**Display name:** "Dire Difficulty". Mod id and namespace **`dire_difficulty`**, Java package
`com.diredifficulty`. It was written under the working name "Advanced Difficulty Settings" with
the id `escalation`; the 2026-08-06 rename moved both, in the one window where moving an id is
legal — nothing published, only local test worlds. **§7.1–§7.5 keep the original name analysis
verbatim as history; §7.6 records what actually shipped and closes the id.**

## 0. Binding rules

Three documents outrank this one and must be read before the first edit:

1. **`../CLAUDE.md`** — the merged onboarding doc for the whole `mc-modding/` folder. House style:
   `Tuning.java` as the single balance file, `platform/` seam discipline, server-authoritative
   state, vanilla-plus ("amplify, don't gate").
2. **`../specialities/docs/MULTIVERSION-CONVENTIONS.md`** — BINDING HERE TOO, exactly as it binds
   Archetypes. Frozen predicate vocabulary, `//?` syntax, the crash-level prohibition on
   directives in any `.json`, §5a *mixin annotations fork, balance logic never*, §5e Java 17 as
   the shared-code ceiling, §5g seam hygiene. Never fetch `stonecutter.kikugie.dev` (R-13).
3. **`../archetypes/docs/ARCHITECTURE.md`** — the shapes this mod copies rather than invents: the
   `StateKey` table, the `*Ticker` pattern, the `hurtServer` damage funnel, and the soft-interop
   bridge (`compat/SpecialitiesBridge`).

**Every Minecraft API named in this document was read off the decompiled 26.2 sources on this
machine**, not written from memory — per the standing instruction. Line references are to
`minecraft-common-043a8b3edf-26.2-sources.jar` and `minecraft-clientOnly-043a8b3edf-26.2-sources.jar`
under `../specialities/.gradle/loom-cache/`. Every verified fact below is marked **VERIFIED** with
its source location. Anything not so marked is a design decision, not a measurement.

---

## 1. What this mod is

A challenge mod that acts as a counterweight to Archetypes' power fantasy. Three systems:

1. **Elite mobs** — regular hostiles roll, on spawn, into rarity-tiered elites with affixes,
   bigger models, a depth-tested rarity glow, and real loot.
2. **Progression score** — a pluggable, config-toggleable 0..1 measure of how far the player has
   got, which drives elite chance and rarity (and optionally spawn rates).
3. **Spawn control** — day spawning, spawn frequency, mob cap, despawn distance.

Wrapped in **four presets**, from "Sunny Day" to "Hell on Earth".

**Vanilla-plus, held to the same line as the other two mods.** No new dimensions, no new ores, no
new menus in the survival flow. Elites are vanilla mobs wearing modifiers. Every affix reads as
something a vanilla mob could plausibly do. Elite loot is *usable resources only* — never building
blocks, never decorative clutter. Nothing an elite does damages terrain.

---

## 2. Elite mobs

### 2.1 Rarity tiers

Four tiers. Rarity is rolled first; it fixes the stat multipliers, the affix slot count, the affix
point budget, the glow colour and the loot tier. **"More simultaneous affixes with progression" is
delivered through rarity, not through a second independent dial** — one input, one ladder, so the
two can never disagree.

| Rarity | Glow / name colour | Slots | Budget | Max HP × | Damage × | Scale × | Loot tier |
|---|---|---|---|---|---|---|---|
| Uncommon | `0x55FF55` green | 1 | 2 | 1.35 | 1.10 | 1.08 | T1 |
| Rare | `0x55AAFF` blue | 2 | 4 | 1.80 | 1.20 | 1.15 | T2 |
| Epic | `0xAA55FF` purple | 3 | 6 | 2.50 | 1.32 | 1.22 | T3 |
| Legendary | `0xFFAA00` orange | 4 | 9 | 3.40 | 1.45 | 1.30 | T4 |

All eight columns live in `Tuning.java`. Nothing else in the tree carries a rarity number.

**Scale is free, mechanically.** `Attributes.SCALE` is on *every* `LivingEntity` by default
(**VERIFIED**: `LivingEntity.createLivingAttributes()` line 344 lists `Attributes.SCALE`), it is a
`RangedAttribute` with default 1.0 / min 0.0625 / max 16.0 and **`.setSyncable(true)`**
(**VERIFIED**: `Attributes.java:89-90`), `LivingEntity.getScale()` reads it
(**VERIFIED**: `LivingEntity.java:560-562`), and an attribute change to `SCALE` triggers
`refreshDimensions()` automatically (**VERIFIED**: `LivingEntity.java:1137-1138`). So one
`AttributeModifier` resizes **both the model and the hitbox**, and the client gets the new size
through vanilla attribute sync — **no packet, no mixin, no render code**.

> **Measured consequence, and it is deliberate.** A vanilla zombie is 1.95 blocks tall, so *any*
> visible upscale (>1.026×) stops it fitting a 2-block door. Elites will path around 2-high gaps
> instead of following you in. That reads as counterplay rather than a bug, so it is accepted —
> but `Tuning.SCALE_ENABLED` exists so a server can turn scaling off and keep everything else.
> Confirm the pathing behaviour in the Stage-1 in-game pass before promising it works.

### 2.2 Rarity glow — depth-tested, NOT the vanilla outline

**The requirement is that the glow must be occluded by walls.** Vanilla's glowing outline is not,
and the reason is structural, not a setting:

- `Entity.setGlowingTag(boolean)` sets shared flag 6 (**VERIFIED**: `Entity.java:2724-2734`,
  `FLAG_GLOWING = 6` at line 267).
- Entities with that flag are rendered a second time into a **separate render target**,
  `entityOutlineTarget` (**VERIFIED**: `LevelRenderer.java:121, 152, 193, 386, 404`), which is then
  blitted back over the finished frame with `RenderPipelines.ENTITY_OUTLINE_BLIT`
  (**VERIFIED**: `RenderPipelines.java:806-812`).
- That blit does not participate in the scene depth buffer. **That is exactly why the vanilla
  outline x-rays**, and it cannot be configured away.

The design instead adds **one extra render layer on the mob's own renderer**, using a
depth-tested emissive render type:

- `RenderTypes.entityTranslucentEmissive(Identifier texture)` (**VERIFIED**:
  `RenderTypes.java:471-476`) resolves to `RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE`, whose
  state is `BlendFunction.TRANSLUCENT`, `withCull(false)`, `ALPHA_CUTOUT 0.1`, shader define
  `EMISSIVE`, and — the load-bearing part —
  **`DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)`** (**VERIFIED**:
  `RenderPipelines.java:353-363`). Reversed-Z: depth **test on**, depth **write off**. Geometry in
  front of the mob occludes the glow. Requirement met.
- The layer draws the mob's **own model a second time** with a rarity ARGB tint, so the glow hugs
  the actual silhouette instead of being a box, and the emissive define keeps it readable at night
  without lighting it like a torch.
- Registration on 26.2: **`LivingEntityRenderLayerRegistrationCallback`** — **VERIFIED** present in
  `fabric-rendering-v1-25.3.1+6988455e9e.jar` (the 26.2 fapi). Note for the port: this class is
  named `LivingEntityFeatureRendererRegistrationCallback` on older fapi. That is a **new predicate
  row** when the port happens; do not guess the boundary, measure it.

**Prior art and the trap it teaches.** Archetypes' `client/NightEyeRenderType.java` header records
a Stage-7 in-game finding worth carrying into this file verbatim: below 1.21.11, vanilla's
`RenderType.eyes` blends through `ADDITIVE_TRANSPARENCY`, which is `blendFunc(ONE, ONE)` — **source
alpha is not a factor in that blend at all**, so an alpha dial written against 26.2 is silently
*inert* on the legacy nodes and a soft glow comes out as a solid band. This design picks
`entityTranslucentEmissive` (translucent on both sides of the boundary) partly for that reason, but
**the port must re-measure the pipeline state per node rather than assume it carried**. That file
also demonstrates the fix shape if it has not: take vanilla's own composite and re-apply exactly
one state shard on top of it.

The client needs to know an entity's rarity to draw this. See §6.5 for the wire format, and note
that scale needs no such thing.

### 2.3 Where the elite roll happens

**`Mob.finalizeSpawn(ServerLevelAccessor, DifficultyInstance, EntitySpawnReason, SpawnGroupData)`**
(**VERIFIED**: `Mob.java:1079-1081`), injected at `TAIL`.

That is the right point, for reasons that are measured rather than assumed:

- It is called by the natural spawner with `EntitySpawnReason.NATURAL` (**VERIFIED**:
  `NaturalSpawner.java:203`) and by chunk generation with `EntitySpawnReason.CHUNK_GENERATION`
  (**VERIFIED**: `NaturalSpawner.java:406`).
- The spawn reason is **an argument in hand**, so the roll can be restricted to those two and skip
  `SPAWNER`, `BREEDING`, `CONVERSION`, `REINFORCEMENT`, `JOCKEY`, `TRIAL_SPAWNER`, `COMMAND`,
  `BUCKET`, `LOAD` and the rest (**VERIFIED**: the full 21-constant enum,
  `EntitySpawnReason.java:4-21`). A mob farm must not print elites; a re-loaded chunk must not
  re-roll one.
- It runs **after** vanilla's own equipment/difficulty finalisation, so elite modifiers layer on
  top rather than being overwritten.

Eligibility: any `Mob` whose `EntityType.getCategory()` is `MobCategory.MONSTER`, minus a
`difficulty:elite_blacklist` tag (bosses, the ender dragon, the wither, anything a pack marks).
Tag-driven so packs can adjust without a code change — house convention.

### 2.4 Elite loot

**`LivingEntity.dropAllDeathLoot(ServerLevel, DamageSource)`** at `TAIL` (**VERIFIED**:
`LivingEntity.java:1500-1509`). The method already computes `boolean playerKilled =
this.lastHurtByPlayerMemoryTime > 0` on its first line, so the "only drop for player kills" gate is
free.

Loot is a **whitelist table in `EliteLoot`, not a blacklist** — that is the enforcement mechanism
for "no building blocks, no decorative clutter". Nothing can leak in.

| Tier | Contents |
|---|---|
| T1 Uncommon | 4–9 iron ingots, 1–3 gold ingots, 2–6 coal, 0–1 enchanted book (levels 1–2) |
| T2 Rare | 10–18 iron, 4–8 gold, 2–4 emeralds, 0–1 diamond, 1 enchanted book (levels 2–3) |
| T3 Epic | 18–30 iron, 8–14 gold, 4–8 emeralds, 1–3 diamonds, 1–2 enchanted books (levels 3–4), 1–2 ender pearls |
| T4 Legendary | 30–48 iron, 14–24 gold, 8–14 emeralds, 3–6 diamonds, 2 enchanted books (near-max), 0–1 netherite scrap |

- **Looting does not multiply elite loot.** The rarity *is* the reward multiplier; letting Looting
  stack on top double-dips. Looting still applies normally to the vanilla table underneath.
- Elite XP is multiplied by `Tuning.eliteXpMultiplier(rarity)` — a separate, smaller dial.
- Amounts, tiers and the whole item list live in `Tuning.java`. `EliteLoot` holds the *shape*, not
  the numbers.

---

## 3. Affix catalogue

25 affixes: the user's 9, plus 16 designed to fit vanilla-plus. Each is a class implementing
`affix.Affix`, registered in `affix.Affixes`.

**Columns.** *Class* drives the structural caps in §4. *Cost* is the point cost against the
rarity budget. *Rolls on* is the eligibility predicate — evaluated against the concrete mob at roll
time, so an affix that cannot function is never drawn rather than being drawn and doing nothing.

### 3.1 The table

| # | Id | Name | Class | Cost | Mechanic (one line) | Rolls on |
|---|---|---|---|---|---|---|
| 1 | `swift` | Swift | MOBILITY | 1 | `MOVEMENT_SPEED` ×1.35 via a permanent modifier. | Any |
| 2 | `relentless` | Relentless | UTILITY | 1 | `FOLLOW_RANGE` ×2.5 and target acquisition ignores line of sight — it aggros through walls and keeps the target. | Any mob with a `targetSelector` |
| 3 | `warlord` | Warlord | SUPPORT | 2 | Non-elite hostiles within 8 blocks get +20% damage and +15% speed while the Warlord lives; the buff drops the tick it dies. | Any |
| 4 | `summoner` | Summoner | SUPPORT | 3 | Every 12s spawns 1–2 non-elite mobs of its own type, hard-capped at 4 alive, and every summon is flagged so it can never itself roll elite or summon. | Any |
| 5 | `chilling` | Chilling Aura | AURA | 2 | Slowness I on players within 5 blocks, refreshed each second. | Any |
| 6 | `searing` | Searing Aura | AURA | 2 | 1.0 magic damage/second to players within 4 blocks. **No knockback on the tick** — it is a pressure aura, not a shove. | Any |
| 7 | `shieldbreaker` | Shieldbreaker | OFFENSE | 2 | A landed melee hit disables the victim's shield for 3s, Warden-style. | Melee-capable |
| 8 | `sunwalker` | Sunwalker | DEFENSE | 1 | Immune to fire and to sunlight burning. | Undead only (i.e. mobs that burn in daylight) |
| 9 | `escapist` | Escapist | DEFENSE | 2 | **Once per lifetime**, at ≤25% HP: teleports 12–20 blocks away and heals 40% of max HP. Flag is persistent state, so it survives a reload and cannot loop. | Any |
| 10 | `warded` | Warded | DEFENSE | 2 | Projectile damage reduced 50%. Melee unaffected. | Any |
| 11 | `rooted` | Rooted | DEFENSE | 2 | `KNOCKBACK_RESISTANCE` 1.0 and `EXPLOSION_KNOCKBACK_RESISTANCE` 1.0 — cannot be pushed, pulled or blasted. | Any |
| 12 | `vampiric` | Vampiric | OFFENSE | 2 | Heals for 35% of the melee damage it deals. Suppressed by the sustain lockout (§4.4). | Melee-capable |
| 13 | `frostbound` | Frostbound | CONTROL | 2 | Hits apply Slowness II for 4s and a brief freeze shiver. | Melee or projectile attacker |
| 14 | `enfeebling` | Enfeebling | CONTROL | 1 | Hits apply Weakness I for 6s. | Melee-capable |
| 15 | `blinkstrike` | Blinkstrike | MOBILITY | 2 | Every ~6s, when >6 blocks from its target and with line of sight, blinks to just behind it — ender-pearl style, no damage on arrival. | Any mob with a target |
| 16 | `stalker` | Stalker | UTILITY | 1 | A landed hit marks the player for 10s: the elite and its allies know the player's position through walls. The player gets a HUD tell so the effect is legible, never invisible. | Melee or projectile attacker |
| 17 | `thorned` | Thorned | DEFENSE | 2 | Reflects 25% of melee damage taken back at the attacker. Does not reflect projectile or magic damage. | Any |
| 18 | `volatile` | Volatile | DEATH | 2 | On death, a 2.0-power explosion. **Block damage OFF, always** — no elite ever grieves terrain. | Any |
| 19 | `splitting` | Splitting | DEATH | 3 | On death spawns 2 non-elite copies at 40% max HP. **One generation only** — the copies carry a persistent `SPLIT_GENERATION` flag and cannot split again. | Small/medium mobs; excluded on anything already `Rooted` or oversized |
| 20 | `noxious` | Noxious | DEATH | 1 | On death leaves a lingering poison cloud, 4-block radius, 8s. | Any |
| 21 | `sundering` | Sundering | OFFENSE | 2 | Hits apply a stacking armour shred: −2 armour per stack, max 4 stacks, 8s, refreshed on hit. | Melee-capable |
| 22 | `volley` | Volley | OFFENSE | 3 | Ranged attacks fire 3 projectiles in a spread; attack cooldown ×1.5 so sustained DPS rises much less than burst does. | Ranged attackers (skeleton family, pillager, illusioner; witch throws 2 potions instead of 3) |
| 23 | `regenerating` | Regenerating | DEFENSE | 3 | Heals 2% max HP/second. Suppressed by the sustain lockout (§4.4). | Any |
| 24 | `bulwark` | Bulwark | DEFENSE | 2 | +6 `ARMOR`, +4 `ARMOR_TOUGHNESS`. | Any |
| 25 | `quaking` | Quaking | OFFENSE | 2 | Every 8s, a shockwave in a 4-block radius: 3 damage and a knockback, telegraphed by a 0.5s wind-up particle. | Melee-capable |

**API notes on the two that are not obvious.**

- **`shieldbreaker` (26.2).** There is **no `Player.disableShield` in 26.2** — **VERIFIED**: a
  tree-wide grep for `disableShield` across all 4849 common sources returns nothing. Shield
  blocking is now the **`BlocksAttacks` data component**
  (**VERIFIED**: `DataComponents.java:206`), and the disable is its own public method:
  **`BlocksAttacks.disable(ServerLevel level, LivingEntity user, float baseSeconds, ItemStack
  blockingWith)`** (**VERIFIED**: `BlocksAttacks.java:79-95`). The blocking stack is found via
  **`LivingEntity.getItemBlockingWith()`** (**VERIFIED**: `LivingEntity.java:3606-3610`). On 1.20.1
  the equivalent is a different method entirely — a boundary for the port, to be measured, not
  remembered. Archetypes already reads `BlocksAttacks` for Instinctive Guard; reuse its reading,
  do not re-derive it.
- **`sunwalker`.** Sunlight burning is gated by **`Mob.isSunBurnTick()`**, a **private** instance
  method (**VERIFIED**: `Mob.java:502`, called from `Mob.java:484`). Private is fine for a mixin
  target. The same injection serves the day-spawn burn immunity in §5.2 — one hook, two callers.

### 3.2 What is deliberately *not* in the catalogue

- No affix adds a raw outgoing damage multiplier beyond the rarity column. Burst is shaped
  (`volley`, `quaking`) or conditional (`sundering`), never a flat number, so §4.4's one-shot
  guarantee has a small surface to hold.
- No affix damages terrain. `volatile` explicitly runs with block damage off.
- No affix steals, drops or damages player inventory. That is the line between "hard" and "spiteful".
- No affix disables player movement outright. `frostbound` and `chilling` slow; nothing stuns.

---

## 4. Exclusion and budget rules

This is the system that makes the promise "no combination can be unkillable or one-shot you"
**structural rather than a matter of reviewing the table**.

### 4.1 The budget

Each rarity carries a **slot count** and a **point budget** (§2.1). Affixes cost 1–3 points. An
elite draws affixes until either the slots are full or the budget cannot afford anything left in
the eligible pool.

**Roll algorithm** (`affix.AffixBudget`):

```
1.  pool  = every registered affix whose eligibility predicate accepts this mob
2.  shuffle pool with the level's RandomSource
3.  picked = {}, spent = 0
4.  for candidate in pool:
5.        if |picked| == rarity.slots                     -> stop
6.        if candidate.cost > rarity.budget - spent       -> skip
7.        if candidate violates a CLASS CAP  (§4.2)       -> skip
8.        if candidate is EXCLUDED by anything in picked  (§4.3) -> skip
9.        picked += candidate ; spent += candidate.cost
10. audit picked against the EHP ceiling (§4.4 B1); drop the
    highest-cost DEFENSE affix and re-audit until it passes
```

The order matters: the class caps and exclusions are checked **before** the pick, and the EHP audit
runs **after** the whole set is drawn. An elite may legitimately end up with fewer affixes than its
slot count — that is the system working, not a failure.

### 4.2 Class caps — the structural half

Evaluated against the set drawn so far. `B` is the rarity's budget.

| Rule | Cap |
|---|---|
| **C1** DEFENSE points | ≤ `ceil(B × 0.5)` **and** ≤ 2 DEFENSE affixes, at every rarity |
| **C2** OFFENSE points | ≤ `ceil(B × 0.5)` |
| **C3** AURA affixes | ≤ 1 (`chilling`, `searing`) |
| **C4** DEATH affixes | ≤ 1 (`volatile`, `splitting`, `noxious`) |
| **C5** MOBILITY affixes | ≤ 1 (`swift`, `blinkstrike`) |
| **C6** SUPPORT affixes | ≤ 1 (`warlord`, `summoner`) |
| **C7** CONTROL affixes | ≤ 1 (`frostbound`, `enfeebling`) |

C1 is the one that matters most: **an elite can never be more than half defensive, and never carry
more than two defensive affixes even at Legendary**. C2 is its mirror. Together they force every
elite to be a mixed thing, which is also what makes them read as *characters* rather than as stat
blocks.

### 4.3 Mutually exclusive pairs — the counterplay half

The class caps stop degenerate *quantities*. This table stops degenerate *combinations*, and each
entry is justified by the specific player option it would close.

The player's outs against an elite are: **(A)** kite it with ranged, **(B)** block with a shield,
**(C)** knock it back or disengage, **(D)** burst it down, **(E)** walk away, **(F)** trade in melee.
**The governing rule: no elite may close more than two of A–F.** These pairs are the ones that
close a third.

| # | Excluded pair | Closes | Why it is barred |
|---|---|---|---|
| X1 | `warded` × `rooted` | A + C | Projectile-resistant *and* unpushable: neither ranged nor knockback works. |
| X2 | `warded` × `blinkstrike` | A + E | Halved ranged damage on something that closes the gap at will — kiting stops existing. |
| X3 | `warded` × `bulwark` | D | Flat armour plus projectile resist means no damage type gets through at a normal rate. |
| X4 | `swift` × `blinkstrike` | E | Two gap-closers. (Also barred by C5; listed so the intent survives a cap edit.) |
| X5 | `regenerating` × `escapist` | D | Heal-over-time plus a full heal-and-flee makes a kill attempt un-finishable. |
| X6 | `regenerating` × `vampiric` | D | Two independent sustain sources on one body. |
| X7 | `thorned` × `searing` | F | Melee is punished twice for occupying the same space. |
| X8 | `quaking` × `shieldbreaker` | B | The shockwave that strips the shield is also the damage — the block channel disappears entirely. |
| X9 | `splitting` × `summoner` | — | An adds-generator that also splits is an unbounded add fight. |
| X10 | `splitting` × `volatile` | — | A death explosion per split copy chains into an area-denial cascade. |
| X11 | `volley` × `blinkstrike` | A + E | A teleporting archer with a triple shot leaves no approach window. |
| X12 | `stalker` × `escapist` | E | It always knows where you are and it can leave when hurt: the fight cannot be ended by either party. |
| X13 | `summoner` × `warlord` | — | The empower aura applied to self-generated adds compounds superlinearly. |
| X14 | `frostbound` × `chilling` | E + F | Two stacking slows reach effective immobilisation. (Also barred by C3+C7; kept explicit.) |
| X15 | `volley` × `relentless` | A | Wall-piercing aggro on a triple-shot archer means being shot before the mob is ever visible. |

Exclusions are **symmetric and declared once**, as a `Set<Pair>` in `affix.Affixes` next to the
registry. A one-directional exclusion is a bug the roll order would hide, so the accessor
normalises the pair.

### 4.4 The two hard guarantees

The tables above are judgement. These two are arithmetic, and they hold no matter what rolls.

**Guarantee A — an elite can never one-shot a healthy player.**
At the tail of the damage funnel, on player victims only, outgoing elite damage is clamped:

```
amount = min(amount, victim.getMaxHealth() * Tuning.ELITE_HIT_CEILING)   // default 0.55
```

Applied **after** every affix contribution and after the rarity damage multiplier. A player at full
health with no armour survives any single elite hit, always. No affix review can break this,
because no affix participates in it.

> **Ordering, and it must be verified by bytecode, not by priority docs.** This handler has to run
> after Skill Proficiencies' damage multipliers and after Archetypes' shapers. Archetypes has the
> precedent — `FlenseMixin` at priority 1500, pinned after Skill Proficiencies' multipliers, with
> the order **verified by mixin bytecode export**. Do the same here and record the measurement.

**Guarantee B — an elite can never be effectively unkillable.**

- **B1, at roll time.** `AffixBudget.audit` computes an effective-HP index against the base mob:
  `hpMultiplier × armourFactor × projectileFactor × sustainFactor`. If it exceeds
  `Tuning.MAX_EHP_INDEX` (default 6.0), the highest-cost DEFENSE affix is dropped and the audit
  re-runs. The published tables already pass, but the audit is what keeps them passing after the
  first retune.
- **B2, at runtime.** `regenerating` and `vampiric` are suppressed for
  `Tuning.SUSTAIN_LOCKOUT_TICKS` (default 80) after the elite takes damage from a player. A player
  who is landing hits always nets the health bar down. `escapist` is once per lifetime, and the
  flag is *persistent* state, so a chunk reload does not refund it.

Both guarantees are in `Tuning.java` and both should get a unit-style probe in the Stage-1
verification pass — they are the two claims this mod makes to players.

---

## 5. Progression score and spawn control

### 5.1 Progression score

Pluggable providers behind `score.ScoreProvider`:

```java
public interface ScoreProvider {
    String id();                                  // config key
    boolean enabled(DireDifficultyConfig config);
    OptionalDouble score(ServerPlayer player);    // 0..1, or ABSENT
}
```

**`OptionalDouble`, not `double`, and that is the whole design.** A provider whose backing mod is
not installed returns **absent**, never 0. Returning 0 would let an enabled-but-unavailable
provider drag the combined score down and silently make the game easier — the same class of bug as
Archetypes' mirrored `SPECIALITIES_SHIFT = 7`, which compiled and drew fine while being seven
pixels wrong (design R-C4). Read live, never assume.

Three providers ship in iteration 1, each independently toggleable:

| Provider | Source | Normalisation |
|---|---|---|
| `archetypes_level` | Archetypes level, 0–60 | `level / 60` |
| `skill_proficiencies_total` | sum of all 15 skill levels, 0–1500 | `total / TOTAL_TARGET` (default 900, i.e. an average of 60 per skill counts as "done") |
| `advancements` | completed advancements, recipe advancements excluded | `count / ADVANCEMENT_TARGET` (default 40) |

Both mod providers use **the `SpecialitiesBridge` shape verbatim** (see
`archetypes/src/main/java/com/archetypes/compat/SpecialitiesBridge.java`): a `static final boolean
LOADED = Platform.INSTANCE.isModLoaded(id)`, and *every* class that names the other mod's API
inside a private static inner holder, so the JVM never resolves those classes when the mod is
absent. Soft dependency, compile-only, no hard requirement in either direction.

**Combining** — config `combineMode`:

- **`MAX`** (default) — the highest score among providers that answered. A player who has done any
  one of these things is progressed; averaging punishes players who engage with only one mod.
- `AVERAGE` — mean over providers that answered.
- `SUM_CLAMPED` — sum, clamped to 1.0.

**From per-player score to a per-spawn score.** Elite rolls happen at a *position*, not for a
player. The score used at a spawn is the **maximum score among players within
`Tuning.SCORE_RADIUS`** (default 128 — matching `NaturalSpawner.SPAWN_DISTANCE_BLOCK`, **VERIFIED**
at `NaturalSpawner.java:59`), falling back to the nearest player if none is in range. Per-player
scores are cached and recomputed every `Tuning.SCORE_REFRESH_TICKS` (default 100): counting
advancements and summing fifteen skills must never run per spawn attempt.

**The curves, all in `Tuning.java`:**

```java
// Elite chance. Slow early, ramps late — a fresh world should feel almost vanilla.
eliteChance(score) = ELITE_CHANCE_MIN
                   + (ELITE_CHANCE_MAX - ELITE_CHANCE_MIN) * pow(score, ELITE_CHANCE_EXP)
    ELITE_CHANCE_MIN = 0.01 ; ELITE_CHANCE_MAX = 0.18 ; ELITE_CHANCE_EXP = 1.6

// Rarity weights, drawn once the elite roll has passed. Legendary is quadratic:
// it is genuinely absent early and genuinely common at the top.
uncommon (score)  = 100 - 60 * score
rare     (score)  =  20 + 35 * score
epic     (score)  =   4 + 30 * score
legendary(score)  =        12 * score * score

// Optional, OFF by default (SPAWN_RATE_MAX_BONUS = 0.0).
spawnRateMultiplier(score) = 1 + SPAWN_RATE_MAX_BONUS * score
```

At score 0: 1% elite chance, and of those 100/20/4/0 → ~81% uncommon, no legendaries.
At score 1: 18% elite chance, 40/55/34/12 → legendaries are ~8.5% of elites.

### 5.2 Spawn control

Four knobs, each a verified hook. **No API here is invented; every one was read off the 26.2
sources.**

**(a) Hostile spawning during the day.**
`Monster.isDarkEnoughToSpawn(ServerLevelAccessor, BlockPos, RandomSource)` — **`public static`**,
**VERIFIED** at `Monster.java:85`. It is the single light gate: `checkMonsterSpawnRules` calls it
(**VERIFIED**: `Monster.java:100-105`) and `checkSurfaceMonstersSpawnRules` calls *that*
(**VERIFIED**: `Monster.java:113-117`). `@Inject(at = @At("HEAD"), cancellable = true)` returning
`true` when `config.daySpawning` opens day spawning for every monster routed through those two
predicates. Mobs already using `checkAnyLightMonsterSpawnRules` are light-agnostic and unaffected —
correct, nothing to do.

Note that `checkSurfaceMonstersSpawnRules` *additionally* requires `level.canSeeSky(pos)`, which is
untouched — so surface monsters still need sky, which is the behaviour we want.

**(b) Day-spawn burn immunity.** Without this, day spawns burn to death within seconds and the
feature does nothing. `Mob.isSunBurnTick()` — **private**, **VERIFIED** at `Mob.java:502`, called
from `Mob.java:484`. Same injection as the `sunwalker` affix; one hook serves both, gated on
`config.daySpawnBurnImmunity` (defaults to `true` whenever `daySpawning` is on).

**(c) Spawn frequency multiplier.**
`NaturalSpawner.spawnForChunk(ServerLevel, LevelChunk, SpawnState, List<MobCategory>)` — **public
static**, **VERIFIED** at `NaturalSpawner.java:116-118`, called once per spawning chunk per tick
from `ServerChunkCache.tickSpawningChunk` (**VERIFIED**: `ServerChunkCache.java:423`).
`@WrapMethod`: run the original `floor(m)` times, plus once more with probability `frac(m)`; for
`m < 1`, run once with probability `m`. Honest and monotone in `m`.

**(d) Mob cap and despawn distance.** `MobCategory` (**VERIFIED**: `MobCategory.java`;
`MONSTER("monster", "MO", 70, false, false, 128)` at line 7, `noDespawnDistance = 32` at line 22).
`@ModifyReturnValue` on three getters, applied to `MONSTER` only:

- `getMaxInstancesPerChunk()` (line 47) — feeds **both** caps: the global one in
  `SpawnState.canSpawnForCategoryGlobal` (**VERIFIED**: `NaturalSpawner.java:522-525`) and the
  per-chunk one in `LocalMobCapCalculator$MobCounts.canSpawn` (**VERIFIED**:
  `LocalMobCapCalculator.java:51-52`). One knob, both caps — that is why this getter is the right
  target and a `SpawnState` mixin is not.
- `getDespawnDistance()` (line 59, default 128) and `getNoDespawnDistance()` (line 63, default 32) —
  the despawn frequency dial, read by `Mob.checkDespawn` (**VERIFIED**: `Mob.java:685-703`).
  Scaling down despawns sooner; scaling up keeps mobs around.

**Out of scope for iteration 1:** `Level.tickCustomSpawners` (patrols, phantoms, wandering traders;
**VERIFIED** called at `ServerChunkCache.java:406`) is left alone. It is a separate, well-isolated
system and dragging it in now buys complexity without buying the feature.

### 5.3 The Hunt — preset 4's focus mechanic

Preset 4 requires mobs that "actively seek the player". Implemented in `TheHunt`, an
`END_SERVER_TICK` listener in the `*Ticker` shape:

Every `Tuning.HUNT_INTERVAL_TICKS` (default 40), for each online player, walk loaded `Mob`s within
`Tuning.HUNT_RADIUS` (default 96). For each that has no current target, `setTarget(player)`.

**The part that is not obvious, and the reason this is a mechanic rather than a one-liner:** a bare
`setTarget` at distance is discarded within a tick or two, because `NearestAttackableTargetGoal`'s
continue-check re-evaluates `TargetingConditions` against `FOLLOW_RANGE`. So the Hunt also applies a
**transient `FOLLOW_RANGE` modifier** sized to the hunt radius for as long as the mob is hunting,
and removes it when the hunt drops — asserted every pass, the same way Archetypes' tickers assert
standing attribute modifiers, which is what makes it respawn- and reload-safe without an event of
its own.

Bounded on purpose: at most `Tuning.HUNT_MAX_PER_PLAYER` (default 12) mobs per player per pass, so
it cannot become an unbounded pathfinding storm on a busy server. **Nothing teleports.** Mobs walk,
they path around obstacles, and terrain still works as cover. Vanilla-plus.

---

## 6. Architecture (iteration 1)

### 6.1 Package layout

```
com.<modid>
├─ Difficulty.java            common init: registration, event wiring, payload registration
├─ Tuning.java                ALL balance constants + curves. One file, no conditionals, all nodes
├─ Rarity.java                enum — colour, slots, budget, stat multipliers, loot tier
├─ EliteRoller.java           spawn interception -> rarity roll -> affix roll -> apply
├─ EliteMob.java              read/write elite state on an entity (thin face over the store)
├─ EliteLoot.java             extra drops on death, whitelist table, rarity-scaled
├─ EliteTicker.java           per-tick auras, regen, summon pulses, sustain lockout
├─ EliteCombat.java           on-hit affix hooks, called from the hurtServer funnel
├─ TheHunt.java               preset-4 focus mechanic
├─ SpawnControl.java          the knob readers the spawn mixins call into
├─ ModState.java              the StateKey table
├─ DifficultyCommands.java    /<modid> score|info|spawn — creative-gated, per Archetypes precedent
├─ affix/
│   ├─ Affix.java             interface: id, class, cost, eligibility, lifecycle hooks
│   ├─ AffixClass.java        enum OFFENSE DEFENSE CONTROL MOBILITY AURA SUPPORT UTILITY DEATH
│   ├─ Affixes.java           THE registry + the exclusion pair set
│   ├─ AffixBudget.java       roll algorithm, class caps, EHP audit
│   └─ impl/                  one class per affix (25)
├─ score/
│   ├─ ProgressionScore.java  combine modes + per-player cache + per-position resolution
│   ├─ ScoreProvider.java     the interface
│   ├─ AdvancementScore.java
│   ├─ ArchetypesScore.java             soft, inner-holder
│   └─ SkillProficienciesScore.java     soft, inner-holder
├─ config/
│   ├─ DifficultyConfig.java  plain public fields, no-arg ctor, Gson-serialised
│   ├─ ConfigManager.java     load/save + rename migration hook (SP's shape, from day one)
│   └─ Preset.java            the four bundles + CUSTOM
├─ state/                     StateKey, WireCodec, WireId  (Archetypes' shapes, copied not shared)
├─ platform/                  THE SEAM
│   ├─ EliteStore.java        interface + static INSTANCE — all attached state
│   ├─ Net.java               interface + static INSTANCE — payload registration + sends
│   ├─ Platform.java          interface + static INSTANCE — config dir, isModLoaded
│   └─ Fabric{EliteStore,Net,Platform}.java     package-private impls
├─ mixin/                     see §6.4
└─ compat/
    ├─ ArchetypesBridge.java
    └─ SkillProficienciesBridge.java

com.<modid>.client
├─ DifficultyClient.java      client init, payload receivers
├─ EliteGlowLayer.java        the depth-tested glow render layer
├─ EliteGlowRenderType.java   the render type (the file that forks hardest at port time)
├─ EliteClientState.java      entityId -> rarity, fed by the payload; cleared on stop-tracking
├─ EliteNameplate.java        rarity-coloured name suffix
└─ config/                    ClothConfigScreen, ModMenuIntegration  (SP's shapes)
```

### 6.2 Server vs client

**The server decides everything.** Rarity, affixes, loot, spawn control, the score, the Hunt. The
client is a renderer with no authority — the same rule both other mods hold.

The client needs exactly two things, and they arrive by different routes:

| Client need | How it gets there |
|---|---|
| **Scale** | Nothing. `Attributes.SCALE` is `setSyncable(true)` and drives `getScale()`/`refreshDimensions()` (§2.1) — vanilla attribute sync delivers model *and* hitbox. **No packet, no mixin, no render code.** |
| **Rarity** (for the glow and the nameplate) | One small clientbound payload — §6.5. |

### 6.3 Persistence

Elite state is an **attachment on the entity**, through the `platform/EliteStore` seam, declared as
a `StateKey` table in `ModState` exactly as Archetypes declares its 74 keys.

| Key | Kind | Why |
|---|---|---|
| `ELITE_RARITY` | persistent, `ALL_TRACKING` | a reloaded chunk keeps its elites; every tracking client needs it to draw |
| `ELITE_AFFIXES` | persistent, server-only | the drawn set, by id |
| `ESCAPIST_USED` | **persistent**, server-only | the once-per-lifetime flag. Persistent on purpose — a chunk reload must not refund it (Guarantee B2) |
| `SPLIT_GENERATION` | **persistent**, server-only | blocks split chains through a reload |
| `SUSTAIN_LOCKED_UNTIL` | transient, server-only | the §4.4 B2 lockout timestamp |
| `SUMMON_COUNT` / `SUMMON_READY_AT` | transient, server-only | summoner bookkeeping and its hard cap |
| `NO_ELITE` | persistent, server-only | stamped on summons and split copies so they can never roll elite |

`ELITE_AFFIXES` stores **ids as strings**, not ordinals. Ordinals reorder when the registry grows;
string keys are how Skill Proficiencies persists external skills and how this stays safe across
versions.

### 6.4 Mixins

Full descriptors, per conventions §5h. Every target below is **VERIFIED** to exist in 26.2 at the
line given.

| Mixin | Target | Injection | Purpose |
|---|---|---|---|
| `MobMixin` | `finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;` | `TAIL` | elite roll (`Mob.java:1079`) |
| `MobSunMixin` | `isSunBurnTick()Z` (private) | `HEAD` cancellable | `sunwalker` + day-spawn burn immunity (`Mob.java:502`) |
| `MonsterMixin` | `isDarkEnoughToSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z` | `HEAD` cancellable | day spawning (`Monster.java:85`) |
| `MobCategoryMixin` | `getMaxInstancesPerChunk()I`, `getDespawnDistance()I`, `getNoDespawnDistance()I` | `@ModifyReturnValue` | mob cap + despawn (`MobCategory.java:47,59,63`) |
| `NaturalSpawnerMixin` | `spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;Ljava/util/List;)V` | `@WrapMethod` | spawn frequency (`NaturalSpawner.java:116`) |
| `LivingEntityMixin` | `hurtServer(...)` | `HEAD` + a high-priority tail handler | on-hit affixes, `thorned`, `warded`, and **Guarantee A's damage ceiling** |
| `LivingEntityLootMixin` | `dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V` | `TAIL` | elite loot (`LivingEntity.java:1500`) |
| `EliteGlowLayer` (client) | *no mixin* — registered through `LivingEntityRenderLayerRegistrationCallback` | — | the glow |

Keep `injectors.defaultRequire: 1` on every mixin config variant. It is the drift detector that
forces every injector to resolve on every node — the convention that caught real bugs in both other
repos.

**Priority, and why it is a measurement.** The Guarantee-A ceiling must sit after Skill
Proficiencies' damage multipliers and after Archetypes' shapers, or a stacked multiplier lands after
the clamp and the guarantee is false. Archetypes solved the identical problem for `FlenseMixin`
(priority 1500) and its architecture doc is explicit that the order was **verified by bytecode
export, not by reading the priority documentation**. Do the same and record the result.

### 6.5 Wire format

One clientbound payload:

```
EliteStatePayload {
    int   entityId
    byte  rarity       // Rarity ordinal; 0xFF == not elite (used to clear)
    int   affixMask    // one bit per registry index; reserved for tooltips
}
```

Sent on `EntityTrackingEvents.START_TRACKING` (**VERIFIED** present in
`fabric-networking-api-v1-6.3.3+72073ef09e.jar`, the 26.2 fapi) and broadcast to trackers when an
elite is assigned. `EliteClientState` drops the entry on stop-tracking.

**Why a payload rather than a synced attachment, decided now rather than at port time.** Fabric
attachment *sync* does not exist below 1.20.5 — fapi 0.92.11 on the 1.20.1 node has the persistent
builder and no `syncWith` at all (frozen vocabulary, the `>=1.20.5` row). Archetypes hit exactly
this and had to build `LegacyStateSync`. A payload on START_TRACKING is the one shape that works
identically on all seven targets, so writing it that way from day one saves a fork that is
otherwise certain. `affixMask` ships unused so the format never has to change to carry it.

### 6.6 The seam, from day one

`com.<modid>.platform` is the only package that may name `FabricLoader`, `AttachmentTarget`,
`AttachmentType`, `AttachmentRegistry`, `AttachmentSyncPredicate`, `PayloadTypeRegistry`,
`ServerPlayNetworking`, `ClientPlayNetworking`, or any NeoForge/Forge API. A call site anywhere else
is drift to catch in review, and **a grep is the review gate** — the same gate both other repos use.

Each seam is an interface plus a `static final INSTANCE` plus a package-private per-loader
implementation. When the port comes, `INSTANCE` becomes a `fabric`/`neoforge`/`forge` **block-form**
chain repeating the whole field declaration — the maintained template's one-line inline `elif`
cannot chain (measured in Skill Proficiencies' Stage 6; a second one fails with
`Unmatched scope closer`).

Two facts to build around now, both already paid for elsewhere:

- **`src/main` cannot reach client-only API.** The `ClientPlayNetworking` receiver for
  `EliteStatePayload` lives in `client/DifficultyClient`. Do not "complete" `Net` with a client
  method.
- **Java 17 is the shared-code ceiling.** Records are fine; pattern-matching `switch`, sealed types
  and `Math.clamp` are not. Writing to that ceiling now costs nothing and saves a sweep later.

### 6.7 Interop

No hard dependency in any direction. Archetypes and Skill Proficiencies do not need to know this
mod exists; this mod reads them through bridges and works fully without either.

- Compile-time: a **per-node file dependency** on `../specialities/build/libs/<v>/` and
  `../archetypes/build/libs/<v>/`, with the versions pinned in `stonecutter.properties.toml` — the
  mechanism Archetypes already uses, for the reason it uses it (one artifact provably cannot serve
  seven nodes when a return type is `Identifier` on 26.x and `class_2960` on the legacy Fabric jars).
  Consequence to write into the README now: a `clean` in either neighbour repo breaks **this repo's
  configuration phase**, not just its compile.
- Runtime: `isModLoaded` + the inner-holder pattern, so the classes are never resolved when absent.
- **HUD:** iteration 1 draws nothing at the bottom of the screen, so this mod does not join the
  `HUD_SHIFT` contract. If a bottom-HUD element is ever added, read
  `SpecialitiesBridge.hudShift()` **live** — never mirror the constant. Archetypes shipped a
  hardcoded `7` applied on mere presence of the other mod, and Skill Proficiencies' 1.6.0 HUD-bar
  toggle made that number 0 at runtime: both builds compiled, both drew, seven pixels apart.
- The word "elite" is a genre term and is fine in-game. The Modrinth project `elitemobs` is a
  Paper/Spigot plugin on a different platform (§7), so there is no product collision — only a
  naming one, which §7 avoids.

---

## 7. Name analysis

Researched live against the Modrinth v2 API (search + per-slug `GET /project/<slug>`) on
2026-08-05.

### 7.1 Is the working name taken?

**No.** `GET /v2/project/advanced-difficulty-settings` → **404**, and a search for the exact phrase
returns **0 hits**. The slug and the display name are both free.

### 7.2 The neighbourhood (verified, with download counts)

| Slug | Title | Platform | Downloads | Relationship |
|---|---|---|---|---|
| `elitemobs` | EliteMobs | paper / purpur / spigot | 64,189 | The name-collision risk. **Plugin, not a mod** — different platform, but "Elite Mobs" as a display name is effectively spoken for. |
| `champions` | Champions | forge | 130,511 | The closest conceptual prior art: affix-based elite mobs. |
| `mobchampions` | Mob Champions | fabric / neoforge | 40,598 | Same idea on our loaders. |
| `eldritch-mobs-modifications` | Eldritch Mobs Modifications | datapack / fabric / forge / quilt | 36,613 | Affix-style mob modifiers. |
| `apocalypse-rebooted` | Apocalypse Rebooted | forge | 53,136 | Difficulty escalation. |
| `majruszs-progressive-difficulty` | Majrusz's Progressive Difficulty | — | 1,020,117 | **The direct competitor to the progression-score feature.** |
| `difficulty-lock` | Difficulty Lock | — | 6,017,608 | Top result for "difficulty"; unrelated function, but it owns the search term. |
| `adaptive-difficulty` | Adaptive Difficulty | — | 1,985 | **TAKEN** — rules out that name. |

Searching `difficulty` returns **298 mods**. The term is crowded and the top slots are held by mods
with millions of downloads doing something else entirely.

### 7.3 Verdict on "Advanced Difficulty Settings"

**Available, but recommend against it**, for three reasons:

1. **It describes the wrong thing.** "Advanced X Settings" reads as a config panel. The product is
   an elite-mob system with a progression engine; the name sells the least interesting third of it.
2. **It lands in the worst part of a crowded search space.** 298 "difficulty" mods, with
   Difficulty Lock (6M) and Majrusz (1M) owning the term. A generic three-word difficulty name
   ranks nowhere.
3. Minor: the acronym is ADS, which reads as a firearms term in this hobby.

### 7.4 Candidates — all confirmed free (slug 404 + no title collision in search)

| Display name | Slug | Mod id | Notes |
|---|---|---|---|
| **Escalation** ← recommended | `escalation` | `escalation` | Names the actual mechanic: difficulty escalates with progression. One word, matches the house style (`specialities`, `archetypes`). Search returns nothing relevant. |
| **Threat Level** | `threat-level` | `escalation` | Very legible; "your threat level rises" maps directly to the progression score. Only search hit is unrelated. |
| **Perilous** | `perilous` | `escalation` | Short, evocative, zero collisions. Says less about what it does. |
| **Dire** | `dire-difficulty` | `escalation` | `Dire Difficulty` returns **zero** search results — completely clear ground. |
| **Advanced Difficulty Settings** | `advanced-difficulty-settings` | `escalation` | The working name, kept as a fallback. Free, just weak. |

Also verified free and available if none of the above land: `grim-difficulty`, `ascendant-difficulty`,
`apex-difficulty`, `difficulty-plus`, `the-gauntlet`, `rising-tide`, `dread`, `perilous-nights`.
(`onslaught` is a free slug but "Zombie Onslaught" exists at 908 downloads — mild, avoidable.)

### 7.5 The mod id — decide once, now

**Recommendation: `escalation`, starting immediately, regardless of which display name is chosen.**

The rule this follows is Skill Proficiencies' hardest-learned lesson, recorded in `CLAUDE.md`: the
project was renamed to "Skill Proficiencies" in 2026-07-18 and **the mod id, namespace, attachment
keys and source directory stayed `specialities` forever**, because changing an id orphans every
world's stored data. Never "fix" the id to match a title.

So the id must be chosen to **survive any later rename**:

- `escalation` names the **mechanic**, not the marketing. It stays true under every candidate
  display name in §7.4, including the working one.
- One lowercase word — consistent with `specialities` and `archetypes`, and a valid mod id on all
  three loaders.
- It is not a common English namespace word likely to collide with another mod's id.
- Explicitly **do not** use `advanced_difficulty_settings` or any id derived from the current
  working name. That is the exact mistake the rename rule exists to prevent.

Everything downstream is stamped from this one decision and cannot be changed later: the namespace
(`assets/escalation/`, `data/escalation/`), the attachment keys (`escalation:elite_rarity`, …), the
payload channel ids, the mixin config names, the source directory `com/escalation/`, and
`rootProject.name`. The **config file name follows the display name** rather than the id — Skill
Proficiencies' GitHub issue #2 — and `ConfigManager` ships the rename-migration hook from day one so
a later rename is a one-line change instead of a settings loss.

---

### 7.6 Outcome — the name that shipped (2026-08-06)

**§7.1–§7.5 above are kept verbatim as the record of the analysis.** They are history; this
subsection is what was actually decided, and it supersedes §7.5's recommendation.

| | Analysis recommended | Shipped |
|---|---|---|
| Display name | Escalation | **Dire Difficulty** (§7.4's "Dire" row — the only candidate returning *zero* Modrinth search results) |
| Mod id / namespace | `escalation` | **`dire_difficulty`** |
| Java package | `com.escalation` | **`com.diredifficulty`** |
| Config file | `advanced-difficulty-settings.json` | **`dire-difficulty.json`** (still display-name-derived, per issue #2) |
| Test command | `/escalation`, alias `/advdiff` | **`/dire`**, alias `/advdiff` |

**Why the id moved at all, when §7.5 says an id is permanent.** §7.5 is right, and the rule is
not weakened by this. An id change orphans stored data, so it is legal in exactly one window:
before anything ships. Nothing had been published, no Modrinth project existed, and the only
worlds carrying `escalation:*` attachment data were local test worlds. That window closed with
this commit. **`dire_difficulty` is now permanent under §7.5's rule** — the display name may
change again, the id may not.

The one thing §7.5 got exactly right in practice: `ConfigManager`'s migration hook, built empty
on day one, made the config half of this rename a two-line change (`FILE_NAME` plus one entry in
`LEGACY_FILE_NAMES`) with no settings lost.

---

## 8. Presets

Four presets on a weather ladder, so the ramp is legible from the names alone.

**Semantics, and they matter for the UX:** selecting a preset **stamps** the individual config
fields. Editing any stamped field flips `preset` to `CUSTOM`. There is no hidden override layer and
no "preset wins over your edit" surprise — what the file says is what runs.

### 8.1 Sunny Day

*Gentler than vanilla. Elites exist as a rare treat, never as a threat.*

| Knob | Value |
|---|---|
| Elite chance | max **0.03**, progression scaling on but clamped |
| Rarities | Uncommon only — 1 slot, budget 2 |
| Affix classes allowed | MOBILITY, UTILITY, SUPPORT only (no AURA, no DEATH, no OFFENSE on-hit) |
| Day spawning | OFF |
| Spawn frequency | ×1.0 |
| Mob cap | ×0.85 |
| Despawn distance | ×0.85 (mobs clear out sooner) |
| The Hunt | OFF |
| Loot | T1 only |

### 8.2 Gathering Clouds

*The intended default. Vanilla, plus a real elite layer that grows with you.*

| Knob | Value |
|---|---|
| Elite chance | max **0.10**, full progression scaling |
| Rarities | Uncommon / Rare / Epic — Legendary locked |
| Affix classes allowed | all |
| Day spawning | OFF |
| Spawn frequency | ×1.0 |
| Mob cap | ×1.0 |
| Despawn distance | ×1.0 |
| The Hunt | OFF |
| Budget / slots | as published in §2.1 |

### 8.3 Storm Front

*For a party with an Archetypes build online. Elites are the content.*

| Knob | Value |
|---|---|
| Elite chance | max **0.22**, full progression scaling |
| Rarities | all four |
| Day spawning | OFF |
| Spawn frequency | ×1.5 |
| Mob cap | ×1.35 |
| Despawn distance | ×1.25 (they stick around) |
| The Hunt | OFF |
| Budget / slots | **+2 budget and +1 slot at Epic and Legendary** |

### 8.4 Hell on Earth

*The spec's preset 4. Progression is ignored; the world is already at maximum.*

| Knob | Value |
|---|---|
| **Progression** | **IGNORED — score pinned to 1.0 always.** Max elite rates from the first night. |
| Elite chance | max **0.35** |
| Rarities | all four, with a Legendary weight floor so they appear immediately |
| **Day spawning** | **ON**, with day-spawn burn immunity ON |
| Spawn frequency | ×2.0 |
| Mob cap | ×1.75 |
| Despawn distance | ×1.6 |
| **The Hunt** | **ON** — §5.3 |
| Budget / slots | **+3 budget, +1 slot** at every rarity |

**Guarantees A and B still hold at Hell on Earth.** The ceiling and the EHP audit are not preset
knobs. The hardest setting is faster, denser and relentless — never a coin-flip death from full
health, and never a mob that cannot be killed.

### 8.5 Config surface

`config/<display-slug>.json`, Gson-serialised from a plain-public-fields class with a no-arg
constructor, exactly as `SpecialitiesConfig` is. Using the Gson bundled with Minecraft — **no
external dependency**, so the config works whether or not Mod Menu and Cloth Config are installed.

The in-game GUI is the Cloth Config screen reached through Mod Menu, mirroring Skill Proficiencies'
26.x setup (`client/config/ClothConfigScreen.java` + `ModMenuIntegration.java`): the screen mutates
the held instance and calls `save()`. Cloth and Mod Menu are gated `>=26.1` in the frozen predicate
vocabulary, and the entrypoint is blanked below that boundary by **`processResources` conditioning**
in the node script — never by a `//?` directive, which does not work in `.json` and is crash-level
in `fabric.mod.json`.

---

## 9. Iteration 1 scope, and what it is not

**In:** the 25 affixes, four rarities, the budget/exclusion system with both guarantees, the three
score providers, the four spawn knobs, The Hunt, elite loot, the depth-tested glow, the four
presets, the Cloth/Mod Menu config, and creative-gated `/<modid>` debug commands.

**Out, deliberately:** custom elite entity types, boss mechanics, elite-specific models or textures,
a bestiary UI, structure-linked elites, `tickCustomSpawners` (patrols/phantoms), and any HUD
element beyond the `stalker` tell.

**Verification bar before anything ships**, matching what both other mods hold themselves to:

1. Headless dedicated server boots clean — zero mixin errors, all injectors resolve
   (`injectors.defaultRequire: 1` proves it), clean stop, exit 0.
2. `Tuning` propagation: a one-character edit moves `Tuning.class` and nothing else.
3. **Guarantee A** probed directly: an elite of every rarity, every offensive affix, against a
   full-health unarmoured player — no single hit exceeds the ceiling.
4. **Guarantee B** probed directly: roll several thousand Legendary elites, assert no drawn set
   violates a class cap, an exclusion pair, or the EHP index.
5. In-game client pass: the glow is **occluded by a wall** (the whole point of §2.2), scale renders
   and the hitbox matches, day spawning produces mobs that do not instantly burn.
6. Mixin ordering measured by bytecode export with all three mods installed — Guarantee A's handler
   after Skill Proficiencies' multipliers and Archetypes' shapers.

Nothing goes to Modrinth without explicit per-release go-ahead from the user.

---

## 10. As built — iteration 1

Written at integration, 2026-08-05, after three parallel agents landed the affix/elite core, the
progression/spawn/config half and the client rendering. **This section outranks §1–§9 wherever
they disagree** — those are the spec, this is the measurement.

Everything below is a deliberate deviation with its reason, or a gap with its consequence.
Nothing here is a discovered bug left standing.

### 10.1 What is verified, and by what

| Claim | Evidence |
|---|---|
| Build green | `./gradlew build`, single node, zero errors and zero warnings. `buildAndCollect` → `build/libs/0.1.0/dire_difficulty-0.1.0+26.2.jar` (154 KB, 89 classes) |
| Headless dedicated server boots clean | `Done (0.806s)`, **zero mixin errors, zero ERROR lines, zero WARN lines**, clean `stop`, **exit 0** |
| All eight server injectors resolve | `injectors.defaultRequire: 1` on both configs makes a boot the proof, and `javap` on the `-Dmixin.debug.export` dumps confirms the handlers by name in all five transformed targets: `LivingEntity` (`dropEliteLoot`, `scaleEliteExperience`, `shapeEliteDamage`), `Mob` (`rollElite`, `sunBurnImmunity`), `MobCategory` (`mobCap`, `despawnDistance`, `noDespawnDistance`), `NaturalSpawner` (`spawnFrequency`), `Monster` (`daySpawning`) |
| **Guarantee B1** (§4.4) | `/dire audit <rarity> 5000` at all four rarities under Storm Front: **0 violations**, worst EHP index 5.83 vs the 6.00 ceiling, mean affix counts 1.00 / 2.00 / 3.99 / 4.88. The probe re-derives the caps, the exclusions, the slot/budget limits and the index from `Tuning` and `AffixClass` rather than asking `AffixBudget` whether `AffixBudget` was right |
| Promotion end to end | `/dire spawn` + `/dire info` + vanilla `/data get entity`: `dire_difficulty:elite_health` `+2.4 add_multiplied_base` on a base-20 zombie → 68.0 max health at Legendary; `dire_difficulty:elite_scale` `+0.30` → `getScale()` 1.30; `CustomName` `{color:"#FFAA00", extra:[{translate:"entity.minecraft.zombie"}]}` — the rarity colour straight out of `Tuning.RARITY_COLOUR`, wrapping the mob's own translatable name |
| Exclusion / cap enforcement is real | `spawn … legendary warded rooted bulwark` reports all four rules it bypassed: `class-cap DEFENSE count 3 > 2`, `exclusion warded x rooted`, `exclusion warded x bulwark`, `ehp 6.56 > 6.00` |
| Loot table (§2.4) | `/dire loot <rarity> 50` at all four tiers, through `EliteLoot.rollDrops` — the shipped path, not a copy. Whitelist holds: iron/gold/coal/emerald/diamond/ender pearl/netherite scrap/enchanted book and nothing else. T1 never produces emeralds, diamonds, pearls or scrap; T4 never produces coal |
| Preset stamping | All four presets applied and read back: Sunny Day `0.03 / tier 1`, Gathering Clouds `0.10 / tier 3`, Storm Front `0.22 / tier 4`, Hell on Earth `0.35 / tier 4 / daySpawning true / theHunt true` |
| Rarity ceiling respected | `spawn … roll` under Sunny Day (`maxRarityTier = 1`) draws Uncommon; under Storm Front it draws across the ladder |

**Smoke harness**: `scratchpad/esc-smoke-final.sh`. It refuses to run without a pre-existing
`eula=true` (copied from `../specialities/run/`, never accepted fresh).

> **One measurement worth keeping.** The harness runs `forceload add ~ ~` first, and that is not a
> workaround for a mod bug. A dedicated server with **no players online keeps no chunk in the
> entity-ticking state**, so a summoned entity sits pending and is invisible to every selector —
> `/data get entity @n[type=zombie]` answers "No entity was found" for a zombie the summon just
> reported spawning, and the pending entities all materialise at once the moment something loads
> the chunk. Any future headless probe that spawns something must force-load first or it will
> measure nothing and look like a broken feature.

### 10.2 Naming and layout

- **Mod id `dire_difficulty`**, permanent under §7.5's rule and closed by §7.6. Display name
  "Dire Difficulty"; the config file follows the display name: `config/dire-difficulty.json`,
  with `ConfigManager.LEGACY_FILE_NAMES` carrying the pre-rename
  `advanced-difficulty-settings.json` so nobody loses a setting. (Iteration 1 shipped this
  section as id `escalation` / name "Advanced Difficulty Settings" with the legacy list empty —
  the hook then did exactly its job at the rename.)
- Class names follow the id, not §6.1's placeholder `Difficulty*`: `DireDifficulty`, `DireDifficultyConfig`,
  `DireDifficultyClient`, `DireDifficultyCommands`.
- **Packages are deeper than §6.1's flat root.** The elite core is `elite/`; the score stack is
  `progression/` (not `score/`); `SpawnControl` and `TheHunt` are `spawn/`; the mixins are
  `mixin/elite/`, `mixin/spawn/` and `client/mixin/`. The two features stay separable in the
  config, in review diffs and at port time, and no two agents' files landed in one directory.

### 10.3 State, and the two plug points

- **No `state/` package and no `ModState` `StateKey` table.** All seven of §6.3's keys collapsed
  into ONE immutable `EliteData` record behind a **named-method** `platform/EliteStore` seam. The
  reasoning is Archetypes' own, run in the other direction: the generic table pays for itself at 74
  keys and ~260 call sites and costs more than it saves at two values on one subject. A later
  `StateKey` table implements this interface without touching a call site.
- **Two plug-point interfaces the design does not name, each with a working default**, so the elite
  half compiles, boots and plays on its own:
  - `progression/ScoreSource` — the NARROW question the elite roll asks (one number, at a position).
    Default `ZERO`; `ProgressionScore implements ScoreSource` and calls `ScoreSource.install`.
    §5.1's wide, per-provider interface kept its own name, `progression/ScoreProvider`.
  - `elite/EliteRules` — the subset of preset knobs the roll reads. Default `GATHERING_CLOUDS`, its
    numbers read live from `Tuning` rather than copied. `ConfigEliteRules implements EliteRules` and
    calls `EliteRules.install`.
- Init order, committed in `DireDifficulty.onInitialize` and asserted by `EliteSystems`:
  `ConfigManager.load` → `ConfigEliteRules.register` → `ProgressionScore.register` →
  `EliteSystems.register` (store, then catalogue, then listeners) → `TheHunt.register` →
  `DireDifficultyCommands.register`. Both plug points are installed before anything can observe the
  built-in fallbacks, and the command tree is last because it names the catalogue and the config.

### 10.4 Wire format — §6.5's payload is not built, and that is the deviation to re-read at port time

**There is no `EliteStatePayload` and no `platform/Net` seam.** `EliteData` is a fabric-api
attachment registered with `AttachmentSyncPredicate.all()`, so fabric-api broadcasts it to every
tracker and `EliteStore.INSTANCE.get(entity)` answers **on the client**. The client reads it live
through the seam — no cache to go stale, nothing to leak as entity ids recycle, nothing to
invalidate on unload or disconnect.

§6.5's argument for a payload is a **port** argument: fapi 0.92.11 on the 1.20.1 node has the
persistent attachment builder and no sync of any kind. That is about *where the fork lands*, not
about what this node does — and the fork lands inside `FabricEliteStore`, behind an interface no
caller can see through, exactly where Archetypes had to build `LegacyStateSync`.

Two format notes so the payload, when it is written, carries the bytes that already exist:

- `EliteData.write`/`read` are spelled in **`FriendlyByteBuf` primitives**, the oldest buffer type
  present on every target.
- `NOT_ELITE` is **`-1` as a var-int**, not §6.5's `0xFF` byte, and rarity is an `int`. There is no
  `affixMask` — the whole record crosses, with affixes as **string ids**. `Affixes.indexOf` still
  keeps the frozen registration index, so a mask remains available without a format change.

### 10.5 Behaviour deviations, each with its reason

1. **The rarity damage multiplier is in the funnel, not on `ATTACK_DAMAGE`.** That attribute
   governs a melee swing and nothing else, so an attribute-based multiplier would silently exempt
   every skeleton's arrow, every witch's potion and every blaze's fireball from §2.1's own Damage
   column. `EliteCombat.outgoing` applies it uniformly, in §4.4's order: affixes, then the
   multiplier, then Guarantee A's ceiling.
2. **`sunwalker` needs no mixin of its own.** Infinite hidden Fire Resistance plus fire-tick
   clearing in the ticker covers the fire half; the sunlight half is a predicate registered into
   `SpawnControl.addSunImmunity`, so `MobSunMixin` is **one hook serving two features** and neither
   feature imports the other.
3. **`stalker`'s tell is vanilla Glowing on the elite**, not a HUD element. Strictly more
   informative (it says *by what*, and *from where*), it doubles as the mark's own timer so the
   affix stores no state, and it keeps this mod out of the `HUD_SHIFT` contract entirely — §6.7's
   own preference.
4. **`volley` is a periodic burst on its own interval**, not a per-shot multiplier plus a cooldown
   multiplier: a ranged mob's cadence lives in its goal, not in an attribute. Burst rises sharply,
   sustained DPS barely moves, which is the property §3.1 is paying 3 points for.
   `Tuning.VOLLEY_COOLDOWN_MULTIPLIER` is therefore `VOLLEY_INTERVAL_TICKS`.
5. **`NaturalSpawnerMixin` uses `@WrapMethod`, and it is forced rather than preferred.**
   `SpawnState.canSpawn`, `afterSpawn` and `canSpawnForCategoryLocal` are all **private** in 26.2,
   so a TAIL replay would need three accessors and a hand-copy of vanilla's loop.
6. **Everything after a hit rides fabric-api's `AFTER_DAMAGE`/`AFTER_DEATH`**, not a second mixin
   handler. Those hand over the damage **actually taken**, after armour, resistance and blocking —
   a number no HEAD or RETURN injection has in hand — so a fully blocked hit applies no debuff and
   a lifesteal cannot heal from damage the victim never took. Two fewer mixins.
7. **The compat bridges use reflection**, not compile-time references. §6.7 specifies a per-node
   file dependency on `../specialities/build/libs/<v>/` and `../archetypes/build/libs/<v>/`;
   adding one now, to read four public primitive-returning methods, would make a `clean` in either
   sibling repo break **this repo's configuration phase**. Deferred to the port, where the
   dependency has to exist anyway. Swapping to direct references is a delete, not a rewrite — the
   public surface is unchanged.
8. **No `maxLootTier` knob.** §2.1 says rarity fixes the loot tier, so Sunny Day's "T1 only" falls
   out of `maxRarityTier = 1`. A separate knob could only ever disagree with the rarity.
9. **`maxRarityTier` is an int 1..4 and `allowedAffixClasses` a `List<String>` (empty means all)**,
   not enums, so the on-disk format survives the catalogue growing. An unknown class name is
   *ignored* by the roller, never a parse failure.
10. **Client: no `EliteNameplate`.** The rarity-coloured name is done server-side in `EliteNameTag`,
    wrapping the mob's own `EntityType.getDescription()` so every client resolves it in its own
    language, with **no rarity word** — a word means four new keys, and an untranslated key renders
    as `elite.dire_difficulty.rarity.legendary` over the mob's head, which is worse than no word.
    `EliteClientState.RENDER_KEY` carries the whole `EliteData`, so a nameplate can be added later
    with no new plumbing.

### 10.6 Client rendering, as built

- **Scale needed no code at all**, and the chain is measured end to end:
  `LivingEntity.createLivingAttributes` adds `Attributes.SCALE` (`LivingEntity:344`), which is
  `setSyncable(true)` (`Attributes:89-90`); `ServerEntity:342` sweeps `getAttributesToSync()` into
  `ClientboundUpdateAttributesPacket`; `ClientPacketListener:2245` applies it and marks dirty;
  `LivingEntity.tick` → `refreshDirtyAttributes` → `onAttributeUpdated` routes SCALE to
  `refreshDimensions()` (`LivingEntity:1137-1138`), which re-derives box and eye height on the
  client too; and `LivingEntityRenderer.extractRenderState:267` sets `state.scale = getScale()`.
  One server-side modifier moves the model **and** the hitbox on every client.
- **The glow is a depth-tested second geometry pass**, not `setGlowingTag`. A `RenderLayer` on
  every living renderer, registered through fabric-api's
  `LivingEntityRenderLayerRegistrationCallback` (no mixin for the layer), re-submitting
  `getParentModel()` at `order(1)` through `RenderTypes.entityTranslucentEmissive` tinted
  `ARGB.color(0x66, rarity.colour())`. That pipeline depth-**tests** but does not depth-**write**
  (`RenderPipelines:353-363`), so terrain occludes it and the same-depth re-submit cannot z-fight;
  it takes no lightmap, so it reads at full brightness in a cave; and it inherits the skin's alpha
  cutout, so it hugs the real silhouette. `affectsOutline = false` plus `NO_OUTLINE` keep it out of
  `entityOutlineTarget`, which is the thing that x-rays.
- **§6.4 says the glow needs no mixin; one client mixin exists anyway, and the table is not
  wrong.** The *layer* needs none. What needs one is the hop from entity to render state:
  `RenderLayer.submit` receives only an `EntityRenderState`, which carries **no entity id and no
  entity reference**. So `LivingEntityRendererMixin` is a single `@Inject(TAIL)` on
  `extractRenderState` that stashes the `EliteData` under a `RenderStateDataKey`. Full descriptor
  in the selector, mandatory: javac's erasure bridge gives that method name two overloads.
- Registered on **every** living renderer rather than filtered to `MobCategory.MONSTER`:
  eligibility is a server rule, and mirroring it client-side would fail silently the day it widens.

### 10.7 The test kit — `/dire`, alias `/advdiff`

Registered as one node tree with a Brigadier **redirect** for the alias, so the two spellings
cannot drift and the client is sent one copy of the arguments.

```
/dire score [player]                        combined score + per-source breakdown + the
                                            elite chance that score buys
/dire info                                  nearest elite within 32 blocks: rarity, hp,
                                            scale, affixes, flags, EHP index
/dire spawn <entity> [rarity|roll] [affixes…]
/dire preset [sunny_day|gathering_clouds|storm_front|hell_on_earth]
/dire audit <rarity> <count>                §9.4's roll probe
/dire loot <rarity> [count]                 §2.4's table, rolled without a corpse
```

- **Gate: permission level 2 AND (a player source is in creative | the source has no player).**
  Archetypes refuses the console because every one of its subcommands mutates the *caller*; this
  kit acts on the world and on the tables, and the console is how the headless smoke test reaches
  it. A command the smoke test cannot invoke cannot be smoke-tested. The survival-playthrough gate
  — the actual point — is unchanged.
- `spawn` takes **no coordinate argument**: vanilla's `/execute positioned x y z run …` already
  moves the source, and a coordinate argument would collide with the greedy affix list. It summons
  through vanilla's own public `SummonCommand.createEntity`, which carries the bounds, peaceful and
  duplicate-UUID checks and finalises with `EntitySpawnReason.COMMAND` — a reason `EliteRoller`
  refuses, so the natural roll cannot fire underneath and double-promote.
- **An explicit affix list bypasses the caps, the exclusions and the EHP audit on purpose** — the
  point of a test kit is to stand in front of the combinations the roller refuses — and every rule
  it bypassed is **printed**, so the bypass is legible and the report doubles as a check that the
  rule fires. Guarantee A is untouched either way: it lives in the damage funnel, which no affix
  and no command can reach.
- Two small seams were opened for the probes, both so the probe measures the shipped path rather
  than a copy: `EliteLoot.rollDrops(level, rarity)` split out of `onDropAllDeathLoot` (the drop
  path is player-kill gated, so a headless server can otherwise never see the table run), and
  `ProgressionScore.providers()`.
- `audit` **re-derives** the rules rather than calling back into `AffixBudget`. A probe that asked
  the roller whether the roller was right could only ever agree with itself.

### 10.8 Known gaps — everything §9's verification bar still owes

1. **Guarantee A has never been measured.** `LivingEntityMixin` claims priority 1500 orders the
   ceiling after Skill Proficiencies' multipliers and Archetypes' shapers. §6.4 requires that be
   verified by **mixin bytecode export with all three mods installed**, not by reading priority
   docs. `-Dmixin.debug.export=true` is already on in this repo's run configs.
   **Guarantee A is false if the claim is wrong and nothing in the build would say so.**
2. **§9.3's ceiling probe is not built** — every rarity × every offensive affix against a
   full-health unarmoured player. It needs a player, so it needs either a fake-player harness or
   the in-game pass.
3. **§9.5's whole in-game client pass is unrun.** Nothing rendering-side is proven by compilation:
   the glow being **occluded by a wall** — the entire point of §2.2 — the scale reading right, the
   hitbox matching, and day spawning producing mobs that do not instantly burn. The jar is in
   `../specialities/run/mods/` for the user's next dev-client launch.
4. **The natural roll path has never fired.** Everything proven above enters through the command.
   `Mob.finalizeSpawn` resolving and `EliteRoller` refusing `COMMAND` are both proven; the
   `NATURAL` branch, the elite-chance curve at a real spawn and the score-at-a-position resolver
   all need a player in the world.
5. **Elite loot has never actually dropped.** The table is proven by `/dire loot`; the
   player-kill gate and `spawnAtLocation` are not.
6. **`MobMixin` TAIL is the tail of `Mob`'s own body**, so a subclass's equipment, baby and
   reinforcement work lands *after* the promotion. Nothing should collide — the modifiers are
   id-keyed, the name is wrapped, health is topped from the multiplied max — but the case to check
   in-game is a **baby zombie**, whose size and speed the subclass settles.
7. **`Tuning` propagation (§9.2) is unproven**: a one-character edit moving `Tuning.class` and
   nothing else. Cheap, and worth doing before the first release.
8. **`/dire score` cannot be exercised from the console** — it needs a player, and a
   headless server has none.
9. **The oversized-elite pathing consequence of §2.1 is unconfirmed.** Any visible upscale stops a
   zombie fitting a 2-block door. It is accepted as counterplay, but it has not been seen.

---

## 11. As built — iteration 1.1, the first in-game look (2026-08-05)

The user launched a 26.2 client against the iteration-1 jar and reported one thing: a Legendary
zombie **"simply looked a bit yellow"** — the rarity overlay read as a skin TINT, not as a glow.
That is the only finding, and it is a real one. This section records what was done about it and
the elite plate that shipped alongside. **It outranks §10 the way §10 outranks §1–§9.**

### 11.1 The glow — diagnosis, not a bigger number

§10.6's pass was correct and still is: `entityTranslucentEmissive` depth-**tests** and does not
depth-**write** (`RenderPipelines:353-363`), takes no lightmap, and inherits the skin's alpha
cutout. Walls occlude it, it reads in a cave, and it hugs the silhouette. None of that changed.

What was wrong was that **one in-silhouette pass at a flat `0x66` cannot look like a glow**, and
the reason is in the pipeline rather than in the constant. `ENTITY_TRANSLUCENT_EMISSIVE`
multiplies the vertex colour by the model texture, so 40% orange over a zombie's dark green-grey
skin is bounded by how bright that skin already is — a muted wash. And it is bounded to the
*inside* of the mob, where there is nothing for a player to read as light coming off a body.

Three changes, all dials in `Tuning.java`, none of them touching the occlusion guarantee:

| Change | What it is | Why |
|---|---|---|
| **Alpha up the ladder** | `GLOW_SKIN_ALPHA = {0x40, 0x70, 0xA0, 0xCC}` | §2.1's own promise, delivered: Uncommon stays a hint so a common spawn does not look like an event; Legendary is meant to be unmistakable at thirty blocks |
| **A halo shell** | `GLOW_HALO_INFLATE = {1.025, 1.040, 1.055, 1.075}`, `GLOW_HALO_ALPHA = {0x18, 0x30, 0x50, 0x74}` | The model re-submitted slightly larger. `ENTITY_TRANSLUCENT_EMISSIVE` is `withCull(false)`, so the shell's BACK faces are drawn too — and they sit behind the mob's own body, which wrote depth in the opaque pass, so they survive **only in the rim**. The rim gets two faces, the middle gets one. That gradient off the edge of the body is the thing a player reads as "glowing", and no alpha on a single in-silhouette pass can produce it |
| **A pulse** | `GLOW_PULSE_AMPLITUDE = 0.30`, `GLOW_PULSE_PERIOD_TICKS = 50` | A static colour is furniture; a moving one is a signal. Driven off `EntityRenderState.ageInTicks` (`entity.tickCount + partialTicks`, `EntityRenderer:168`) rather than level time, so **each elite carries its own phase** — a pack lit off one clock strobes in lockstep, per-entity phase reads as several things breathing |

**The hard rule holds, and here is the argument rather than the assertion.** Both passes go
through the same render type, so both depth-test. The shell inflates by at most 7.5%, which at a
zombie's 0.6-block half-width protrudes about **2 cm** — it cannot clear a wall the mob's own
model does not already clear. `affectsOutline = false` on the type and `NO_OUTLINE` on every
submit keep both passes out of `entityOutlineTarget`, whose blit is the thing that x-rays.

**It is still not a light source.** The emissive define means no lightmap is sampled, so an elite
reads at the bottom of a cave — but nothing is written into the level's block light, so the floor
under it stays dark. The alpha ceiling is `0xCC` on a *translucent* blend, so a Legendary is never
an opaque coloured statue and never washes out a night sky.

**Halo pivot, and it is arithmetic rather than taste.** At layer time the pose stack is where
`LivingEntityRenderer.submit` left it: `translate(0, -1.501, 0)` after a `scale(-1, -1, 1)` flip,
so the origin is the mob's shoulders and +Y runs *down* to the feet at 1.5. Scaling about the raw
origin would sink a Legendary's feet ~11 cm into the floor. `GLOW_HALO_PIVOT_Y = 0.75` is exactly
half of vanilla's own 1.501 offset — the body centre for anything that stands on the ground — and
it halves and centres the drift. The push/pop around the shell is safe against the submit inside
it because `SubmitNodeCollection.submitModel` takes `poseStack.last().copy()`: the node keeps a
snapshot, not a reference.

**Aura motes, Epic and up** (`client/EliteAuraParticles`). `ParticleTypes.ENTITY_EFFECT` takes a
`ColorParticleOption` (`ColorParticleOption.create(type, argb)`, verified public static), so the
mote is `Tuning.RARITY_COLOUR` by construction rather than by a second palette. Client-side only —
nothing is spawned server-side, nothing crosses the wire, nothing is authoritative. It runs on
`ClientTickEvents.END_CLIENT_TICK` rather than in the render layer, because **a frame count is a
frame rate**: the same elite would emit twice as fast at 120 fps as at 60. Bounded three ways
(`AURA_PARTICLE_INTERVAL_TICKS` 3, `AURA_PARTICLE_RADIUS` 32, `AURA_PARTICLE_MAX_PER_PASS` 16 plus
a per-elite `AURA_PARTICLE_CHANCE` so the cap is not always spent on whichever entities
`entitiesForRendering()` lists first). Vanilla's `SpellParticle` carries `gravity = -0.1F`, a
slight *upward* acceleration, so `AURA_PARTICLE_DRIFT_Y = -0.06` produces motes that drip and then
hang rather than rain.

**Deviation from §10.6 worth naming.** Iteration 1 kept `GLOW_ALPHA` in the client class,
arguing (correctly, for the siblings) that a pure render dial belongs with the renderer. Every
glow, particle and plate number now lives in `Tuning.java` on the standing instruction that
**balance and visual constants share that one file**. `Rarity` gained `glowSkinAlpha()`,
`glowHaloAlpha()` and `glowHaloInflate()` so the enum still carries no number and no client class
indexes a `Tuning` array directly.

### 11.2 The elite plate — self-contained, no WTHIT, no Jade

`client/ElitePlateTracker` (input) + `client/ElitePlate` (draw), gated by
`DireDifficultyConfig.elitePlate`, default on, in the Cloth screen's Elites tab.

```
                   Zombie                <- the elite's own name, in the rarity colour
    [==================        ]         <- vanilla boss-bar sprites, rarity-matched
                  Vampiric               <- the drawn affixes, container grey
                 Shieldbreaker
                   Quaking
```

**The pick.** `Minecraft.hitResult` is clipped to interaction reach, so a plate built on it would
only appear once you were already inside a Legendary's swing. Instead
`ProjectileUtil.getHitResultOnViewVector(camera, EntitySelector.CAN_BE_PICKED, 32)` — public
static, read off the 26.2 sources. Using vanilla's helper rather than a hand-rolled ray **buys the
wall rule for free**: its first act is `level.clipIncludingBorder(new ClipContext(from, to,
COLLIDER, NONE, source))` and it truncates the entity search to the block hit, so an elite behind a
wall is never returned. One rule, both surfaces — the plate cannot become an x-ray for the same mob
whose glow §2.2 works so hard to keep depth-tested. A non-elite standing in the way hides the plate
exactly as it hides the mob.

**The linger is a display delay, not a cache.** `PLATE_LINGER_TICKS = 20`. Only the entity
reference is held; health, rarity and the affix list are re-read every frame from the synced
attachment, so a lingering plate shows health dropping while you look away, and an elite that
stops being one disappears on the next frame rather than at the end of the second. Four conditions
drop the target immediately instead of lingering: level gone, entity dead, entity removed, entity
no longer an elite.

**Vanilla chrome, taken literally — this mod ships no new texture.** The bar is *vanilla's boss
bar*: same `boss_bar/*` sprite ids, same 182×5 geometry, same `Mth.lerpDiscrete` fill, same
`RenderPipelines.GUI_TEXTURED`, read off `BossHealthOverlay.extractBar`. The palette matches by
construction — vanilla ships seven boss-bar colours and four of them **are** the four rarity
colours (green / blue / purple / yellow against §2.1's green / blue / purple / orange), so the
rarity ordinal indexes the sprite array directly and the bar cannot disagree with the glow. No
panel, no border, no backdrop: vanilla draws boss-bar names as bare shadowed text and so does this.
The name sits at `barY - 9` and the bar 91 px either side of centre, i.e. **the plate is drawn
exactly where the next boss bar would have gone**.

**Below the boss bars, and it needed one accessor.** `Minecraft.getInstance().gui.hud
.getBossOverlay()` is public all the way down, but `BossHealthOverlay` exposes only
`shouldPlayMusic` / `shouldDarkenScreen` / `shouldCreateWorldFog`, all three of which are `false`
for an ordinary boss bar — none of them can even answer "is there one". The count is a private
`Map<UUID, LerpingBossEvent> events` (`BossHealthOverlay.java:54`), so
`client/mixin/BossHealthOverlayAccessor` is a one-method `@Accessor` and nothing else.
`ElitePlate.belowBossBars` then **mirrors vanilla's own loop** (`y = 12`, `y += 10 + 9` per bar,
`break` at `guiHeight / 3`) rather than multiplying, which is what reproduces the ceiling: on a
server with a dozen boss bars the plate stacks against the same limit the bars do instead of
sliding off the screen.

**The anchor is a measurement.** Attached after `VanillaHudElements.BOSS_BAR` because vanilla's
`Hud` calls `extractBossOverlay` *unconditionally* — the emptiness test lives inside
`BossHealthOverlay.extractRenderState`, not around the call — so fabric's wrapper runs on every HUD
frame and an element attached to it runs with it. Skill Proficiencies learned the other half of
this rule the hard way: an element anchored to `EXPERIENCE_LEVEL` is skipped whenever vanilla skips
its anchor, which it does at zero XP levels. Being inside the `Hud`'s `!isHidden` branch also gives
F1 for free. The anchor governs draw ORDER only; the plate's Y comes from the live bar count.

**No numeric health read-out, deliberately.** Vanilla boss bars have none and Diablo's elite plate
has none; a "68 / 68" over a 5-pixel bar is the first piece of custom chrome that would make the
rest look like an overlay. Numbers live in `/dire info`.

### 11.3 Wire format — unchanged, and that is the finding

**No wire change was needed for either feature, and none was made.** §10.4's `EliteData` already
carries the affix ids **as strings** and already syncs the whole record to every tracker
(`FabricEliteStore` registers it with `AttachmentSyncPredicate.all()`). So the affix names the
plate lists have been on every client since iteration 1 — merely unread. The client-side additions
are pure readers of state that was already there: no payload, no new attachment, no new sync
predicate, and `EliteStore`/`EliteData` are untouched.

The one thing that did have to be added is **display names**: `Affix.nameKey()`, a default method
returning `affix.dire_difficulty.<id>` derived from the frozen id so a key and an id cannot drift, plus
25 entries in `en_us.json`. An id this build does not know is *skipped* by the plate rather than
printed, matching what `Affixes.resolve` does server-side and for the same reason.

### 11.4 Verification, and what is still owed

| Claim | Evidence |
|---|---|
| Build green | `./gradlew build`, zero errors, zero warnings. `buildAndCollect` → `build/libs/0.1.0/dire_difficulty-0.1.0+26.2.jar` (164 KB) |
| Headless dedicated server still boots clean | `Done (0.231s)`, **zero ERROR lines, zero WARN lines, zero mixin errors**, clean `stop`, **exit 0**. The render code is client-only, so what this proves is that the common-side changes (`Tuning`, `Rarity`, `Affix.nameKey`, the new config field) break nothing server-side |
| The plate toggle is genuinely not preset-governed | The server rewrote `run/config/advanced-difficulty-settings.json` (the pre-rename file name — see §7.6) with `"elitePlate": true` and `"preset": "GATHERING_CLOUDS"` — `sanitize()` did **not** flip the label to Custom, which it would have if the field had been added below the governed banner |

**Still owed, and it is the same gap §10.8 named**: the whole in-game client pass. Nothing
rendering-side is proven by compilation. Specifically unseen: the halo reading as a rim rather than
as a bubble, the pulse period feeling slow rather than nervous, the Legendary alpha being
unmistakable at thirty blocks without being blinding at night, the motes not fogging a horde, the
plate's Y landing correctly under one and under several boss bars, and — the one that matters —
**both glow passes and the plate all being occluded by a wall**. The jar is in
`../specialities/run/mods/` and in the Fabulously Optimized profile for the next launch.

### 11.5 Parked for iteration 2 — WTHIT / Jade integration

A plugin that puts the elite's rarity, health and affixes into **WTHIT** or **Jade**'s own overlay,
for players who already run one. Deliberately NOT built now, for two reasons:

1. **It would be an addition to §11.2's plate, never a replacement.** A read-out that only appears
   for players who happen to run a third mod is not a feature, so the self-contained plate has to
   exist either way — and once it does, the plugin is a convenience rather than a capability.
2. It is a real dependency surface: two APIs, two `IWailaPlugin`-style entrypoints declared in
   `fabric.mod.json`, and both soft — the `isModLoaded` + inner-holder shape §5.1 already
   specifies, so neither class is ever resolved when its mod is absent. That is a self-contained
   piece of work with no bearing on anything above it, which is exactly what makes it a clean
   iteration-2 item.

The data it would need is already synced and already read: `EliteClientState.eliteOf(entity)`
answers on the client, and `Affix.nameKey()` gives the labels. A plugin would be a new file and no
change to anything in `elite/`, `platform/` or `client/`.

---

## 12. As built — iteration 1.2: names, gear, gear-drop config (2026-08-05)

Three features on user spec, landed after the glow/plate pass. **This section outranks §11 the
way §11 outranks §10.** Every Minecraft API named below was read off the decompiled 26.2
sources on this machine (`minecraft-common-043a8b3edf-26.2-sources.jar`), per the standing
instruction; the line numbers given are that jar's.

### 12.1 Fantasy names

Every elite is named at promotion. The display form is
**`<Fantasy Name> (<Rarity> <Mob type>)`** — "Grave Lord (Legendary Zombie)".

**The two halves are stored and resolved differently, and that split is the design.**

| Half | Form | Where it lives |
|---|---|---|
| `Grave Lord` | a LITERAL string | generated once, stored in `EliteData.name` |
| `(Legendary Zombie)` | a `translatable` component, rebuilt every time it is displayed | `elite.dire_difficulty.name` + this mod's four `elite.dire_difficulty.rarity.*` keys + the mob's own `EntityType.getDescription()` |

Fantasy names are not translated in any game that has them, and a name assembled from lang keys
would need one key per word and would still come out in English word order everywhere else. The
suffix is the opposite case: it is two ordinary nouns, so it translates, and building it from
the mob's own description means this mod ships **no string for any of the several hundred mob
types it can promote**. Measured, straight out of `/data get`:

```
{translate: "elite.dire_difficulty.name",
 with: ["Grave Lord", {translate: "elite.dire_difficulty.rarity.legendary"},
                      {translate: "entity.minecraft.zombie"}],
 color: "#FFAA00"}
```

**Casing is a lang value, not a code decision.** `elite.dire_difficulty.rarity.*` ships capitalised
("Legendary") so the suffix reads consistently against vanilla's own capitalised entity names.
Lower-casing the entity half in code is not an option that exists — `getDescription()` is a
component the CLIENT resolves, so case-folding it server-side would first have to bake the
server's locale into a stored name. A server that wants "(legendary Zombie)" edits one lang
value.

**Semi-related to the affixes, by weighted merge.** `elite/EliteNames` holds three word banks —
per-affix (all 25 ids), per-mob-family (12), and a generic bank — and one elite's pool is the
three merged at `Tuning.NAME_AFFIX_WEIGHT` / `NAME_FAMILY_WEIGHT` / `NAME_GENERIC_WEIGHT`
(3/2/1). A name drawn purely from the family would say nothing about the fight; a name drawn
purely from the affixes would spell out a list the plate already prints. At 3/2/1 a Vampiric
zombie is likeliest to be a "Blood" something, then a "Grave" something, then a "Dread"
something — so the name *suggests* the fight without being a second read-out.

Four shapes, rarity-scaled for grandeur:

| Shape | Example | Gate |
|---|---|---|
| `<Prefix> <Title>` | Grave Lord | any |
| `<Prefix><tail>` compound | Bloodwind | any, `NAME_COMPOUND_CHANCE` |
| `<Title> of <Domain>` | Summoner of the Gloom | tier ≥ `NAME_DOMAIN_FROM_TIER` (Rare) |
| `…, the <Epithet>` | Hoar Devourer, the Iron-Willed | Epic 12%, **Legendary 45%**, never below |

**Determinism.** Seeded from the entity's own UUID mixed with the rarity and the drawn affix
ids, through a `RandomSource` of its own — never the level's, so two elites promoted in the same
tick cannot correlate. Storage is what actually guarantees stability across a reload; seeding
this way is what makes the generator *testable* and what keeps a name from drifting if the
banks ever grow. **Measured**: one wither skeleton promoted in boot A, world saved, server
stopped, boot B read `Stonechill (Legendary Wither Skeleton)` and the identical equipment set
back off disk.

**Backward compatible with no migration.** `name` is `Codec.STRING.optionalFieldOf("name", "")`
and is written LAST on the wire, so an iteration-1.1 elite already in a world loads clean and
simply has no fantasy name; `EliteNameTag` falls back to `elite.dire_difficulty.name.plain`, i.e.
just the suffix. Observed in the smoke run — a leftover elite from the previous session's world
printed `name: -` and rendered normally.

**One source, both surfaces.** `EliteNameTag.compose` is the only place the display name is
built. The server stamps it as the custom name; `ElitePlate` draws `entity.getName()`, so the
plate and the in-world nameplate cannot disagree and the plate needed **no change at all**. The
tag is still set WITHOUT `setCustomNameVisible`, unchanged from iteration 1: vanilla then draws
it only for the entity under the crosshair, which is the difference between "I can identify
this thing" and a screen of floating labels during a raid.

**One deviation from §10.5's point 10, and it is the point being reversed on instruction.**
That entry argued against a rarity WORD because four new keys risk rendering as
`elite.dire_difficulty.rarity.legendary` over a mob's head. The keys now ship, in the same file as
the 25 affix names that already had exactly that exposure, so the risk is the one the mod
already carries.

### 12.2 Elite gear

`elite/EliteGear`. Melee-capable elites spawn armed and armoured, on a rarity ladder.

**Everything goes through `Mob.setItemSlot` and `Mob.setDropChance`, and then stops.** Nothing
in the feature spawns an item, rolls a drop or damages a stack — that is what buys vanilla's own
drop behaviour rather than approximating it. The two lines that matter, from
`Mob.dropCustomDeathLoot` (`Mob.java:887-912`):

```java
if (!itemStack.isEmpty() && !EnchantmentHelper.has(itemStack, PREVENT_EQUIPMENT_DROP)
        && (killedByPlayer || preserve) && this.random.nextFloat() < dropChance) {
    if (!preserve && itemStack.isDamageableItem()) {
        itemStack.setDamageValue(itemStack.getMaxDamage()
                - this.random.nextInt(1 + this.random.nextInt(Math.max(itemStack.getMaxDamage() - 3, 1))));
    }
```

So: player-kill gated, rare, and **damaged on the way out** — the "no full-durability windfalls"
rule is vanilla's line, not a reimplementation of it.

**The ladder** (`Tuning`, one shared material index: 0 leather/wood, 1 copper, 2 gold,
3 chainmail/stone, 4 iron, 5 diamond, 6 netherite). Indices 0–5 are **vanilla's own numbering**
from `Mob.getEquipmentForSlot(slot, type)` (`Mob.java:988`), so the armour half is vanilla's
table rather than a copy of it; that method returns `null` for anything above 5, which is why
netherite is this mod's own four-entry row.

| Rarity | Weapon chance | Weapon tiers | Armour chance/slot | Armour tiers | Weapon enchant | Drop chance |
|---|---|---|---|---|---|---|
| Uncommon | 0.55 | gold / stone / iron | 0.30 | copper / gold / chainmail | — | 0.085 |
| Rare | 0.75 | stone / iron ×2 / diamond | 0.50 | gold / chainmail / iron ×2 | 0.15 | 0.075 |
| Epic | 0.90 | iron / diamond ×2 | 0.70 | iron ×2 / diamond | 0.50 | 0.060 |
| Legendary | 1.00 | **netherite** | 0.90 | diamond ×2 / **netherite** | **1.00** | 0.050 |

A repeated entry is a heavier draw. `GEAR_AXE_CHANCE = 0.30` picks an axe over a sword at the
same material. Enchantments come from **vanilla's own roller** —
`EnchantmentHelper.enchantItem(random, stack, cost, registryAccess, IN_ENCHANTING_TABLE)`, the
same call `EliteLoot` uses for its books, at `GEAR_ENCHANT_COST_MIN/MAX` per rarity — so the
level-appropriate selection, the multi-enchantment chance and the incompatibility filtering are
all vanilla's, and restricting to the table's own set is what keeps curses and treasure
enchantments off gear that is going to drop. A measured Legendary: Sharpness 3 / Knockback 2 /
Sweeping Edge 3 on a netherite sword.

The drop-chance ladder **starts at vanilla's own default** (`DropChances
.DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F`) and tapers, because the same 8.5% on an enchanted
netherite sword is a different economy from 8.5% on an iron one.

**The two hard rules.**

1. **Netherite armour never drops.** Drop chance `0.0F` on those slots at equip time, and
   re-asserted at death for any netherite armour that reached the mob another way. The weapon
   is deliberately not covered — the spec says the weapon may drop, and a damaged netherite
   sword at 5% is a trophy rather than an economy. **Measured**, `/data get … drop_chances`:
   `{mainhand: 0.05f, head: 0.05f, chest: 0.05f, legs: 0.0f}` on a Legendary zombie whose legs
   were netherite; `{head: 0.0f, feet: 0.05f, chest: 0.05f, legs: 0.05f}` on a Legendary
   skeleton whose helmet was.
2. **No full-durability windfalls.** `Tuning.GEAR_DROP_CHANCE_MAX = 0.95` is not a taste dial:
   `DropChances.PRESERVE_ITEM_DROP_CHANCE_THRESHOLD` is `1.0F`, and a slot above it is
   *preserved*, which is exactly the branch that drops every time AND skips the
   `setDamageValue` line. Nothing in this mod can write one.

**Eligibility is a tag, and it has to be.** `dire_difficulty:elite_gear` ships vanilla's humanoid
hostile set (zombie family, skeleton family, piglins, illagers). The question the tag answers is
"does this mob RENDER armour and a held item", and **no runtime property exposes it** — a
creeper accepts a netherite chestplate, takes the armour points and draws nothing, which is a
hidden buff with no tell. Tag-driven so a pack can extend it without a code change; an absent
tag resolves empty, so deleting it turns the feature off rather than breaking it. **Measured**:
a Legendary creeper came out bare.

**Weapons go to melee mobs only** — `Eligibility.melee(mob) && !Eligibility.ranged(mob)`. A
skeleton, a pillager and a drowned each have their own weapon and their own attack goal keyed to
it. They still get armour. **Measured**: a Legendary skeleton came out
`mainhand=Bow@0.085, head=Netherite Helmet@0.000, chest=Diamond Chestplate+1@0.050, …`.

**Only empty slots are filled, and that is the whole ordering argument.** The promotion runs at
the TAIL of `Mob`'s *own* `finalizeSpawn`, i.e. inside the `super` call a subclass makes before
its own equipment work (`Zombie.java:449` then `:485`; `AbstractSkeleton.java:118` then `:120`).
Both directions were read rather than assumed:

- `Mob.populateDefaultEquipmentSlots` fills a slot only `if (itemStack.isEmpty())`
  (`Mob.java:979`), so **vanilla never overwrites armour this mod placed**.
- `AbstractSkeleton.populateDefaultEquipmentSlots` sets `MAINHAND` to a bow
  **unconditionally** (`AbstractSkeleton.java:111`), so a skeleton keeps its bow whatever this
  mod does — which is the wanted behaviour anyway, and the `ranged` test above stops the
  pointless stack being made in the first place. A wither skeleton's stone sword is the same
  vanilla line and was observed doing exactly that.

**No interaction with elite loot, checked rather than assumed.** `EliteLoot` injects at the TAIL
of `LivingEntity.dropAllDeathLoot` and adds items from its own whitelist; equipment drops inside
`Mob.dropCustomDeathLoot`, which that method calls earlier. Neither reads or writes the other's
items. **Gear drops are in addition to the rarity loot table, with no double handling in either
direction.**

### 12.3 The two gear-drop toggles

| Config field | Default | Effect when off |
|---|---|---|
| `eliteGearDrops` | `true` | an elite never drops its worn equipment |
| `allMobGearDrops` | `true` | **no** mob drops naturally spawned worn equipment, elite or not |

Both are in the Cloth screen's Elites tab, next to `elitePlate`, and **neither is
preset-governed** — whether worn equipment drops is an economy decision, not a difficulty one.
Hell on Earth arms its elites either way.

**The hook is `Mob.dropCustomDeathLoot` at HEAD**, `mixin/elite/MobGearDropMixin`. That method is
where worn equipment drops in 26.2 — the loop over `EquipmentSlot.VALUES`, the
`dropChance != 0.0F` gate, the `killedByPlayer || preserve` gate, the `PREVENT_EQUIPMENT_DROP`
check, the looting adjustment through `EnchantmentHelper.processEquipmentDropChance` and the
`setDamageValue` line all live inside it, and nothing above or below it touches equipment.

**It suppresses by zeroing vanilla's own drop chance, not by cancelling the loop.** Everything
else that loop does stays exactly as vanilla wrote it, and a cancel would additionally swallow
`super.dropCustomDeathLoot`, which is `LivingEntity`'s and is not this feature's business.
**Measured** in the transformed class (`-Dmixin.debug.export`, `javap -c`): the handler is
`invokespecial` at **offset 0**, ahead of the `super` call at offset 12 and the loop at 15.

**Preserved slots are never touched, and that is a safety rule.** A drop chance above 1.0 means
the mob *picked that item up* — `Mob.equipItemIfPossible` calls `setItemSlotAndDropWhenKilled`,
which calls `setGuaranteedDrop` (`Mob.java:553-563`). Suppressing those would eat items a player
threw or lost, which no difficulty setting should be able to do. It is also what makes toggle
(b) mean exactly what it says: no *naturally spawned* gear drops, while gear the world handed
the mob still comes back.

### 12.4 Verification

| Claim | Evidence |
|---|---|
| Build green | `./gradlew build`, zero errors, zero warnings |
| Headless dedicated server boots clean | `Done (0.187s)`, **zero ERROR lines, zero WARN lines, zero mixin errors**, clean `stop`, **exit 0** — with the new mixin in the config under `injectors.defaultRequire: 1` |
| The new injector resolves where it claims to | `javap -c` on the exported `Mob`: `handler$…$direDifficulty$gearDropRules` at offset **0** of `dropCustomDeathLoot`, before `super` and before the equipment loop |
| Name end to end | `/dire spawn` + `/data get … CustomName` → `{translate: "elite.dire_difficulty.name", with: ["Hollow Lord", {translate: "elite.dire_difficulty.rarity.legendary"}, {translate: "entity.minecraft.zombie"}], color: "#FFAA00"}` |
| Names are varied and affix-related | `/dire names` — 14/14 distinct at Legendary, 8/8 at Uncommon, 8/8 for skeleton, 8/8 for illager, 6/6 for arthropod. `Sanguinerot` (vampiric), `Bloodwind` (vampiric+swift), `Solar Dawnbringer` (sunwalker), `Summoner of the Gloom` (summoner), `Quake Sigilbearer` (quaking+warded) |
| **Same elite, same name after a reload** | two boots against one world: `Stonechill (Legendary Wither Skeleton)` and its whole equipment set read back identical off disk, zero ERROR/WARN in either boot |
| Iteration-1 elites still load | a leftover elite with no `name` field deserialised clean and printed `name: -` |
| Gear ladder | Legendary zombie: `mainhand=Netherite Sword+3, head=Diamond Helmet, chest=Diamond Chestplate+1, legs=Netherite Leggings+2`. Uncommon zombie: `chest=Copper Chestplate@0.085, legs=Copper Leggings@0.085, feet=Golden Boots@0.085` |
| **Netherite armour never drops** | `drop_chances` NBT: `{mainhand: 0.05f, head: 0.05f, chest: 0.05f, legs: 0.0f}` (netherite legs) and `{head: 0.0f, …}` (netherite helmet) |
| Tag gate holds | Legendary creeper: `gear: -` |
| Ranged mobs keep their weapon | Legendary skeleton: `mainhand=Bow@0.085` plus a full armour set |
| **The toggles are genuinely not preset-governed** | a hand-seeded `{"preset":"HELL_ON_EARTH","eliteGearDrops":false,"allMobGearDrops":false}` loaded with the label **intact** (the "names a preset only" path still fires), and after a later `dire preset gathering_clouds` the file still read `"eliteGearDrops": false, "allMobGearDrops": false` |
| Guarantee B1 unaffected | `/dire audit legendary 4000` → **0 violations**, worst EHP index 5.83 vs the 6.00 ceiling; `uncommon 4000` → 0 violations |

Harnesses: `scratchpad/esc-smoke-gear.sh` (features) and `scratchpad/esc-smoke-reload.sh` (the
two-boot name-persistence test). Both refuse to run without a pre-existing `eula=true`.

### 12.5 Still owed

1. **No player kill has ever happened**, so the drop path itself — vanilla's roll, its damage
   line, and both toggles actually suppressing — is proven by reading vanilla's source and by
   the drop-chance NBT, not by seeing an item hit the ground. A headless server has no player;
   this needs the in-game pass. It is the same shape of gap as §10.8's item 5.
2. **The name has never been SEEN**: over a mob's head under the crosshair, and on the elite
   plate. Both draw the same component, and the plate needed no change, so the risk is layout
   (a Legendary two-part name is long) rather than correctness.
3. `/dire names` rolls affixes against the live preset, so its output reflects whatever
   bonus slots the preset grants. That is deliberate but worth knowing when reading a log.
4. The gear feature adds one more thing for §10.8's item 6 (`MobMixin` TAIL vs a subclass's own
   finalisation) to confirm in-game — a **baby zombie in a full set** is now the case to look
   at.

---

## 13. Second in-game look, 26.3 (2026-11-21) — **outranks everything above**

Port: the single node moved 26.2 → 26.3 (commit 1c7c9a8).

User pass results: wall hides the glow ✔, elite plate (name/HP/affixes) ✔, upscale visible ✔,
**natural spawn path fired** (Legendary creeper, unprompted) ✔, **loot dropped** on a player kill ✔.

Changes from it:

1. **Glow → aura.** The skin sheen + inflated shell read as "a tinted copy of the texture, not
   bright". Replaced (`EliteGlowLayer`/`EliteGlowRenderType` deleted) by `client/EliteAura`:
   the mob is drawn with raised block light (`AURA_MOB_LIGHT`), a soft additive camera-facing
   disc sits behind the body so light shows around the silhouette, and a soft additive pool is
   projected onto block tops around the feet (vanilla-shadow-style block search). Own pipeline:
   lightning shader + lightning blend, depth tested, NO depth write; OIT set mirrors vanilla's
   lightning. Drawn from a `LivingEntityRenderer.submit` HEAD inject (pose at entity origin).
   Walls still hide all of it. Real block lighting of the area is NOT done (no dynamic light in
   vanilla; would need light blocks or LambDynamicLights interop) — option for later.
2. **Guarantee A retired** (user decision): no one-shot ceiling; elites can kill a full-health
   player in one hit. `ELITE_HIT_CEILING` removed. Guarantee B (never unkillable) stays.

### 13.x Loot nerf (2026-11-21, user decision)

Elites must never make mining pointless. One material per kill, by weight from the tier's column, 1-2 of it: uncommon none, rare iron, epic iron/gold, legendary iron/gold/emerald/diamond. Enchanted books legendary only, one. Coal, ender pearls and netherite scrap dropped from the whitelist. Numbers in `Tuning` LOOT_WEIGHT_* / LOOT_MATERIAL_* / LOOT_BOOK_*.

### 13.y Presets retired, stage-based progression (2026-11-21, user decision)

- No presets. World difficulty caps the progression stage: Peaceful 0, Easy 0.25, Normal 0.5, Hard 1 (hardcore = Hard).
- Stage = weighted sum of four vanilla stats, each eased out (`1-(1-x/T)^2`, exactly 1 at target): advancements 0.35 (T 60, recipes excluded), hostile kills 0.25 (T 1000, MONSTER-category `killed` stats), distance 0.20 (T 100k blocks, all movement stats except creative flight), time since death 0.20 (T 3 h). Weights sum to 1 → no single factor exceeds its weight; diminishing returns within each. Chosen over "one stat can max out" and over pure asymptotes (which never reach 1).
- SP/Archetypes score providers + compat bridges deleted: mod must mean the same in any pack.
- Stage drives: elite chance (0.01 → 0.35, exp 1.6), rarity weights, spawn frequency (per chunk, 1 → `spawnFrequencyAtMax`), mob cap + despawn distance (highest online stage, 1 → `…AtMax`). Bonus affixes / legendary floor / affix-class whitelist gone (fixed 0 / all).
- Config: General (spawnElites, modifySpawns, theHunt), Elites (elitePlate, eliteGearDrops default OFF, eliteGlow), Spawning (daySpawning, daySpawnBurnImmunity, three `…AtMax` multipliers). `allMobGearDrops` removed → vanilla handles non-elite gear. Multipliers renamed so old files' values are ignored.
- `/dire preset` removed; `/dire score` prints the four factors, cap and stage.

### 13.z Mythical tier + artefacts (2026-11-21, user decision)

- `Rarity.MYTHICAL`, fifth column in every per-rarity Tuning array. Colour 0xFF3A3A, red boss-bar plate, 5 slots / 12 budget, damage x3, scale 1.45, xp x6, netherite gear, voice pitch 0.5. Never drawn by the normal roll (`MAX_RARITY_TIER` = 4).
- Health: rarity x4 plus a 1000 floor; vanilla's MAX_HEALTH attribute caps it at 1024 (512 hearts, Warden 250). Full knockback resistance. EHP audit unchanged (index uses the multiplier, so defensive affixes are still trimmed).
- `MythicalSpawns`: NATURAL — zombified piglin/Nether Wastes, piglin/Crimson Forest, skeleton/Soul Sand Valley, wither skeleton in fortress pieces, pillager in an outpost (chance 0.004); STRUCTURE — piglin brute, vindicator, evoker (bastions, mansions; 0.08; may run off-thread, so only a cached any-player-unlocked flag); EVENT — vindicator/evoker of a raid's final or bonus wave (0.25). Gate: nearest player killed the Ender Dragon (advancement) or a Wither (stat). Natural/raid: no living mythical within 128, 20-min per-dimension cooldown.
- `Artefact`: 13 fixed items, one per Mythical (by mob type, the slot that shows it), guaranteed drop (`setGuaranteedDrop`, survives `eliteGearDrops` off). Styled custom name (mythical red, bold, not italic), grey italic lore, EPIC rarity, custom data `dire_difficulty_artefact`. Shield = offhand +4 armour +2 toughness (Protection does nothing on a shield). Galeskippers = +40% speed attribute (Speed II equivalent). Golden sword keeps 32 durability by design (mend it).
- `/dire artefact <id>` gives one; `/dire spawn <entity> mythical` spawns one.

### 13.w Artefacts II, dominance, Overworld endermen, footsteps (2026-11-21)

- Artefact flavour lines removed (user: read as AI-written); names only. Coded artefacts carry one plain blue line saying what they do.
- 10 more artefacts: Hillstriders (step height +0.5), Springheel Greaves (jump +0.1, safe fall +4), Featherweight Mantle (gravity -30%, safe fall +3), Longreach (entity reach +1), Heartwood Plate (+8 max health), Titan's Crown (scale +15%), Quarrier's Fist (break speed +50%, underwater mining), Bloodletter (20% lifesteal on melee), Second Wind (cancels one death per 10 min, cooldown stored on the item; works on mobs too), Nighteye Helm (night vision while worn). Coded ones: `ArtefactEffects`.
- Mythical endermen: any natural Overworld enderman spawn (same gate, chance, cooldown). Carry Longreach / Featherweight / Titan's Crown / Nighteye / Quarrier's (unseen, guaranteed drop).
- `EliteDominance`: Legendary+ elite with no target, every 2 s 20% chance to attack the nearest visible non-elite monster of another type within 10 blocks (never Warden/bosses), then a 60 s pause. Brain mobs get ATTACK_TARGET memory too. A player nearby takes over through vanilla targeting.
- Footsteps: own volume column (Mythical x8 → ~19 blocks) + ravager-step layer at pitch 0.55 from Epic up (Minecraft has no EQ). User: "nailed it".
- `/dire` tab-completion: command tree re-sent on game-mode change (`ServerPlayerGameModeMixin`); the creative-only gate made suggestions depend on the mode at join.
- Icons: mob-head drafts, `docs/art/make_icons.py` (textures read from the local client jar, output gitignored).

### 13.v Rufus the Bull + icon (2026-11-21)

- Easter egg, nod to the farm channel's giant bull. Natural/chunk-gen adult cow in a `spawns_warm_variant_farm_animals` biome, chance 0.003, no dragon gate (respects spawnElites). Becomes Mythical (no affixes, no artefact), warm variant, +35% extra scale, fixed name "Rufus the Bull", persistent, entity tag `dire_difficulty_rufus`.
- AI (re-installed on every ENTITY_LOAD, goals aren't saved): PanicGoal removed; Charge (MeleeAttackGoal, speed x1.8) with its own hit because cows have no ATTACK_DAMAGE attribute — 8 base (x3 Mythical funnel = 24), knockback 3.0 + lift 0.45; HurtByTargetGoal (charges whoever hits him); NearestAttackableTargetGoal on every `Enemy`.
- Spotting range x2 (FOLLOW_RANGE +100%, 32 blocks), user request after in-game pass (knockback + speed confirmed right).
- `/dire rufus` spawns him. AI verified in-game only (headless servers stop ticking mobs with no player).
- Mod icon: draft E (wither skeleton head, red aura) → `assets/dire_difficulty/icon.png`.

### 13.u Stonecutter workspace, 26.3 + 26.2 (2026-11-21)

- Converted to the sibling repos' shape: `settings.gradle.kts` (nodes `26.3-fabric`, `26.2-fabric`), `stonecutter.gradle.kts` (active + VCS point `26.2-fabric`), `stonecutter.properties.toml` (all pins), `build.fabric.gradle.kts` (one node script + mod-publish-plugin 2.1.1, dry run unless `-PpublishLive=true`). `src/` on disk is in 26.2 form.
- 26.3 deltas: replacements `.hurtMarked`→`.syncVelocity`, `placeItemBackInInventory(stack)`→`+ Prediction.SERVER_ONLY`; inline `//?` for `randomTeleport`'s 26.3 block predicate; `EliteAura` pipeline block (26.3: renderpearl + LIGHTNING_SNIPPET + OIT set; 26.2: blaze3d, vanilla LIGHTNING spelled out from MATRICES_FOG_SNIPPET, no OIT).
- `mod.version` 1.0.0 (first public release). Modrinth project id goes into toml `mod.modrinth_id` once the project exists.
- Both jars build from one `./gradlew buildAndCollect`; both boot a headless server clean and run `/dire spawn … mythical`, `/dire rufus`, `/dire loot`. Client aura on 26.2 unverified in-game.
