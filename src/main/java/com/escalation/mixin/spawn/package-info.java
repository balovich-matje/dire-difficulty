/**
 * The four spawn-control mixins — {@code docs/DESIGN.md} §5.2.
 *
 * <p>A subpackage of {@code com.escalation.mixin} rather than a flat list, because the mixin
 * config's {@code package} is the parent and the entries are then written {@code spawn.XxxMixin}.
 * That keeps this feature's mixins and the elite core's separable in the config, in a review
 * diff, and at port time when only some of them fork.
 *
 * <table>
 * <caption>Targets, all verified present in the decompiled 26.2 sources at the line given</caption>
 * <tr><th>Mixin</th><th>Target</th><th>Injection</th><th>Line</th></tr>
 * <tr><td>{@code MonsterMixin}</td><td>{@code isDarkEnoughToSpawn}</td><td>{@code HEAD} cancellable</td><td>Monster:85</td></tr>
 * <tr><td>{@code MobSunMixin}</td><td>{@code isSunBurnTick} (private)</td><td>{@code HEAD} cancellable</td><td>Mob:502</td></tr>
 * <tr><td>{@code MobCategoryMixin}</td><td>the three cap/despawn getters</td><td>{@code @ModifyReturnValue}</td><td>MobCategory:47,59,63</td></tr>
 * <tr><td>{@code NaturalSpawnerMixin}</td><td>{@code spawnForChunk}</td><td>{@code @WrapMethod}</td><td>NaturalSpawner:116</td></tr>
 * </table>
 *
 * <p>Two standing rules apply to all four:
 *
 * <ul>
 * <li><b>Annotations fork, logic never.</b> Every handler body here is a delegation to
 *     {@code spawn/SpawnControl}, which carries no version-dependent code at all. When the
 *     port lands, only the annotations and parameter lists may sit inside a conditional.</li>
 * <li><b>{@code injectors.defaultRequire: 1} stays on every config variant.</b> It is the
 *     drift detector that forces all four injectors to resolve on every node.</li>
 * </ul>
 */
package com.escalation.mixin.spawn;
