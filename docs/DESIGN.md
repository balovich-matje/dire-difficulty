# Advanced Difficulty Settings — design (iteration 1)

**Status:** DESIGN ONLY. Nothing built. This document is the spec that iteration 1 implements.
**Target:** 26.2 Fabric, single node, architected for the same seven-target Stonecutter port the
other two mods already carry.
**Working display name:** "Advanced Difficulty Settings". See §7 — the name analysis recommends
changing it; the **mod id decision in §7.4 is the one thing that must be made before the first
commit**, because it is permanent.

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
    boolean enabled(EscalationConfig config);
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

## 8. Presets

Four presets on a weather ladder, so the escalation is legible from the names alone.

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
