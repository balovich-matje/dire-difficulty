/**
 * The three elite-core mixins — {@code docs/DESIGN.md} §6.4.
 *
 * <p>A subpackage of {@code com.escalation.mixin} for the same reason {@code spawn} is one:
 * the mixin config's {@code package} is the parent, so the entries read {@code elite.XxxMixin}
 * and the two features stay separable in the config, in a review diff, and at port time when
 * only some of them fork.
 *
 * <table>
 * <caption>Targets, all verified present in the decompiled 26.2 sources at the line given</caption>
 * <tr><th>Mixin</th><th>Target</th><th>Injection</th><th>Line</th></tr>
 * <tr><td>{@code MobMixin}</td><td>{@code finalizeSpawn}</td><td>{@code TAIL}</td><td>Mob:1079</td></tr>
 * <tr><td>{@code LivingEntityMixin}</td><td>{@code hurtServer}</td><td>{@code @ModifyVariable} at {@code HEAD}, priority 1500</td><td>LivingEntity:1169</td></tr>
 * <tr><td>{@code LivingEntityLootMixin}</td><td>{@code dropAllDeathLoot}, {@code dropExperience}</td><td>{@code TAIL}, {@code @ModifyArg}</td><td>LivingEntity:1500, 1514</td></tr>
 * </table>
 *
 * <p><b>Three is the whole list, and the count is the point.</b> Design §6.4 also tables a
 * {@code MobSunMixin} shared with the day-spawn feature; that one lives in {@code spawn}
 * because the config flag half of it does, and the {@code sunwalker} affix reaches the same
 * behaviour without an injection at all (see {@code SunwalkerAffix}). Everything that happens
 * <i>after</i> a hit or a death rides fabric-api events rather than a mixin, because
 * {@code AFTER_DAMAGE} reports the damage actually taken after armour, resistance and
 * blocking — a number no HEAD or RETURN injection on {@code hurtServer} has in hand.
 *
 * <p>Two standing rules apply to all three:
 *
 * <ul>
 * <li><b>Annotations fork, logic never.</b> Every handler body here is a single delegation to
 *     {@code elite/EliteRoller}, {@code elite/EliteCombat} or {@code elite/EliteLoot}, none of
 *     which carries a mixin, loader or version token. When the port lands, only the
 *     annotations and parameter lists may sit inside a conditional — the promotion, the
 *     damage funnel and the loot table stay one implementation across all seven targets.</li>
 * <li><b>{@code injectors.defaultRequire: 1} stays on every config variant.</b> It is the
 *     drift detector that forces all four injectors here to resolve on every node.</li>
 * </ul>
 *
 * <p>One outstanding measurement is owed before anything ships, and it is recorded on
 * {@code LivingEntityMixin} itself: priority 1500 is a claim about ordering against Skill
 * Proficiencies and Archetypes, and §6.4 requires it to be <b>verified by mixin bytecode
 * export</b> rather than by reading the priority documentation. Guarantee A is false if that
 * claim is wrong, and nothing else in the build would say so.
 */
package com.escalation.mixin.elite;
