package com.diredifficulty.elite;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.Affixes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The damage funnel — every affix that touches a damage number, and <b>Guarantee A</b>.
 *
 * <p>Two entry points, from two different places, and the split is the design:
 *
 * <ul>
 * <li>{@link #modifyDamage} runs at the HEAD of {@code hurtServer}, from a mixin pinned at
 *     priority 1500. It shapes the number.</li>
 * <li>{@link #afterDamage} runs from fabric-api's {@code ServerLivingEntityEvents.AFTER_DAMAGE},
 *     which hands over the damage that was <b>actually taken</b> after armour, resistance and
 *     blocking. Every consequence of a hit — lifesteal, debuffs, the shield disable, the
 *     reflect, the sustain lockout — keys off that rather than off the number the funnel was
 *     handed, so a hit that was fully blocked applies nothing and a lifesteal cannot heal from
 *     damage the victim never took. No mixin, and the number is exact rather than nearly
 *     right.</li>
 * </ul>
 *
 * <p><b>Guarantee A, stated as arithmetic.</b> On player victims, outgoing elite damage is
 * clamped to {@code maxHealth x ELITE_HIT_CEILING}, applied after every affix contribution and
 * after the rarity multiplier. A player at full health with no armour survives any single
 * elite hit, always. <b>No affix participates in the clamp</b> — that is the point of it. No
 * review of the affix table can break a guarantee the affix table cannot reach.
 *
 * <p><b>The rarity damage multiplier lives here rather than on {@code ATTACK_DAMAGE}.</b> That
 * attribute governs a melee swing and nothing else, so an attribute-based multiplier would
 * silently exempt every skeleton's arrow, every witch's potion and every blaze's fireball from
 * §2.1's own Damage column. Applying it in the funnel covers all of them uniformly, and it is
 * also the order §4.4 specifies: affixes, then the multiplier, then the ceiling.
 *
 * <p><b>Ordering against the sibling mods is a MEASUREMENT, not a priority-doc reading.</b>
 * The ceiling has to sit after Skill Proficiencies' damage multipliers and after Archetypes'
 * shapers, or a stacked multiplier lands after the clamp and the guarantee is simply false.
 * Archetypes solved the identical problem for {@code FlenseMixin} at priority 1500 and its
 * architecture doc is explicit that the order was verified by mixin bytecode export rather
 * than by reading the documentation. The same verification is owed here — with all three mods
 * installed, {@code -Dmixin.debug.export=true}, and the handler order read off the transformed
 * {@code LivingEntity}.
 */
public final class EliteCombat {
	private EliteCombat() {
	}

	/**
	 * Shapes one damage number. Called for EVERY {@code hurtServer} on the server, so the
	 * two "is this anything to do with us" tests come first and cost one attachment read each.
	 */
	public static float modifyDamage(final LivingEntity victim, final ServerLevel level,
			final DamageSource source, final float damage) {
		float amount = damage;
		Entity attacker = source.getEntity();

		if (attacker instanceof Mob attackingMob) {
			amount = outgoing(level, attackingMob, victim, source, amount);
		}

		if (victim instanceof Mob victimMob) {
			amount = incoming(level, victimMob, source, amount);
		}

		return amount;
	}

	/** The elite is dealing this hit. Affixes, then the rarity multiplier, then the ceiling. */
	private static float outgoing(final ServerLevel level, final Mob mob, final LivingEntity victim,
			final DamageSource source, final float damage) {
		EliteData data = EliteMob.data(mob);

		if (data == null || !data.isElite()) {
			return damage;
		}

		float amount = damage;

		for (final String id : data.affixes()) {
			Affix affix = Affixes.byId(id);

			if (affix != null) {
				amount = affix.modifyOutgoing(level, mob, victim, source, amount);
			}
		}

		amount *= (float) data.rarity().damageMultiplier();

		// GUARANTEE A. Players only: an elite may still delete another mob in one hit, and
		// nothing in the design says otherwise.
		if (victim instanceof Player) {
			amount = Math.min(amount, victim.getMaxHealth() * Tuning.ELITE_HIT_CEILING);
		}

		return amount;
	}

	/** The elite is taking this hit. {@code warded} is the only stock affix that shapes it. */
	private static float incoming(final ServerLevel level, final Mob mob, final DamageSource source,
			final float damage) {
		EliteData data = EliteMob.data(mob);

		if (data == null || !data.isElite()) {
			return damage;
		}

		float amount = damage;

		for (final String id : data.affixes()) {
			Affix affix = Affixes.byId(id);

			if (affix != null) {
				amount = affix.modifyIncoming(level, mob, source, amount);
			}
		}

		return amount;
	}

	/**
	 * Consequences of a hit that landed, driven by fabric-api's AFTER_DAMAGE.
	 *
	 * @param taken the damage actually applied, after armour, resistance and blocking
	 */
	public static void afterDamage(final LivingEntity victim, final ServerLevel level,
			final DamageSource source, final float taken) {
		Entity attacker = source.getEntity();

		if (attacker instanceof Mob attackingMob && taken > 0.0F) {
			onHitLanded(level, attackingMob, victim, source, taken);
		}

		if (victim instanceof Mob victimMob) {
			onDamageTaken(level, victimMob, source, taken);
		}
	}

	private static void onHitLanded(final ServerLevel level, final Mob mob, final LivingEntity victim,
			final DamageSource source, final float taken) {
		EliteData data = EliteMob.data(mob);

		if (data == null || !data.isElite()) {
			return;
		}

		for (final String id : data.affixes()) {
			Affix affix = Affixes.byId(id);

			if (affix != null) {
				affix.onHitLanded(level, mob, victim, source, taken);
			}
		}
	}

	private static void onDamageTaken(final ServerLevel level, final Mob mob, final DamageSource source,
			final float taken) {
		EliteData data = EliteMob.data(mob);

		if (data == null || !data.isElite()) {
			return;
		}

		// GUARANTEE B2, and it is armed here rather than inside the two affixes it governs
		// because it has to be armed even when neither is present: whether a player is
		// currently landing hits is a property of the fight, not of the affix set. Armed on
		// PLAYER damage only — an elite that walked into lava must not be able to lock out
		// its own healing.
		if (source.getEntity() instanceof Player && taken > 0.0F) {
			EliteMob.setTimers(mob, EliteMob.timers(mob)
					.withSustainLockedUntil(level.getGameTime() + Tuning.SUSTAIN_LOCKOUT_TICKS));
		}

		for (final String id : data.affixes()) {
			Affix affix = Affixes.byId(id);

			if (affix != null) {
				affix.onDamageTaken(level, mob, source, taken);
			}
		}
	}

	/** Death-triggered affixes, driven by fabric-api's AFTER_DEATH. */
	public static void afterDeath(final LivingEntity entity, final ServerLevel level,
			final DamageSource source) {
		if (!(entity instanceof Mob mob)) {
			return;
		}

		EliteData data = EliteMob.data(mob);

		if (data == null || !data.isElite()) {
			return;
		}

		Rarity rarity = data.rarity();

		for (final String id : data.affixes()) {
			Affix affix = Affixes.byId(id);

			if (affix != null) {
				affix.onDeath(level, mob, rarity, source);
			}
		}
	}
}
