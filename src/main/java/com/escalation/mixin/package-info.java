/**
 * Common mixins. Empty in the scaffold; {@code escalation.mixins.json} is wired and lists
 * nothing yet.
 *
 * <p>The targets this package will hold are enumerated with full descriptors and verified
 * 26.2 line numbers in {@code docs/DESIGN.md} §6.4: {@code MobMixin} (the elite roll at
 * {@code finalizeSpawn} TAIL), {@code MobSunMixin}, {@code MonsterMixin},
 * {@code MobCategoryMixin}, {@code NaturalSpawnerMixin}, {@code LivingEntityMixin} and
 * {@code LivingEntityLootMixin}.
 *
 * <p>Two standing rules apply to every one of them:
 *
 * <ul>
 * <li><b>Annotations fork, balance logic never.</b> When the port lands, only the annotation
 *     and the parameter list may sit inside a version conditional; the handler body delegates
 *     to a shared unique method outside every conditional, so there is one implementation on
 *     all seven targets.</li>
 * <li><b>{@code injectors.defaultRequire: 1} stays on every config variant.</b> It is the
 *     drift detector that forces every injector to resolve on every node, and it has caught
 *     real bugs in both sibling repos.</li>
 * </ul>
 */
package com.escalation.mixin;
