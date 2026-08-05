package com.escalation.elite;

import com.escalation.Escalation;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Attribute plumbing shared by the rarity multipliers and by every affix that grants a stat.
 *
 * <p><b>Permanent, id-keyed, replace-not-add.</b> Every modifier this mod applies goes on with
 * {@code addOrReplacePermanentModifier} under a stable {@link Identifier}. That is what makes
 * the whole promotion <i>idempotent</i>: re-running it on an entity that already carries it —
 * which happens the moment a reloaded chunk hands back an elite that was saved mid-life —
 * replaces each modifier instead of stacking a second copy. The failure mode this closes is
 * silent and compounding: a Legendary that survived four reloads would be at 3.40 to the
 * fourth power of base health, with nothing in any log to say so.
 *
 * <p>Permanent rather than transient because permanent modifiers are what vanilla serialises
 * with the entity. The elite state is persistent, so its stats have to be too, or a reloaded
 * elite keeps its name tag and its affixes and quietly loses its health multiplier.
 *
 * <p><b>The one attribute this file does NOT special-case is the interesting one.</b>
 * {@code Attributes.SCALE} is on every {@code LivingEntity} by default, is
 * {@code setSyncable(true)}, drives {@code getScale()}, and triggers
 * {@code refreshDimensions()} on change — so a single modifier here resizes both the model and
 * the hitbox on every client with no packet, no mixin and no render code. It goes through the
 * same call as everything else, and that is the whole implementation of §2.1's scaling.
 */
public final class EliteAttributes {
	private EliteAttributes() {
	}

	/** Namespaced modifier id. The namespace is the frozen mod id, so these never collide. */
	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Escalation.MOD_ID, path);
	}

	/**
	 * Adds or replaces a permanent modifier. A no-op when the entity has no such attribute —
	 * a creeper has no {@code ATTACK_DAMAGE}, and asking for one is not an error.
	 */
	public static void set(final LivingEntity target, final Holder<Attribute> attribute,
			final Identifier modifierId, final double amount, final AttributeModifier.Operation operation) {
		AttributeInstance instance = target.getAttribute(attribute);

		if (instance == null) {
			return;
		}

		instance.addOrReplacePermanentModifier(new AttributeModifier(modifierId, amount, operation));
	}

	/**
	 * Multiplies an attribute by {@code factor}, expressed the way vanilla expresses a
	 * multiplier: {@code ADD_MULTIPLIED_BASE} of {@code factor - 1}. Two modifiers of this
	 * shape add rather than compound, which is the intended reading of "x1.35 and x1.20".
	 */
	public static void multiply(final LivingEntity target, final Holder<Attribute> attribute,
			final Identifier modifierId, final double factor) {
		set(target, attribute, modifierId, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	/** Drops a modifier if present. Used by the transient buffs the ticker asserts and releases. */
	public static void clear(final LivingEntity target, final Holder<Attribute> attribute,
			final Identifier modifierId) {
		AttributeInstance instance = target.getAttribute(attribute);

		if (instance != null) {
			instance.removeModifier(modifierId);
		}
	}

	/** Whether a modifier this mod owns is currently on an entity. */
	public static boolean has(final LivingEntity target, final Holder<Attribute> attribute,
			final Identifier modifierId) {
		AttributeInstance instance = target.getAttribute(attribute);
		return instance != null && instance.hasModifier(modifierId);
	}

	/**
	 * Adds or updates a TRANSIENT modifier — one vanilla does not serialise. The Warlord aura
	 * uses this and only this: an aura that persisted through a save would outlive the
	 * Warlord that granted it, and design §3.1 requires the buff to drop the tick it dies.
	 */
	public static void setTransient(final LivingEntity target, final Holder<Attribute> attribute,
			final Identifier modifierId, final double amount, final AttributeModifier.Operation operation) {
		AttributeInstance instance = target.getAttribute(attribute);

		if (instance == null) {
			return;
		}

		instance.addOrUpdateTransientModifier(new AttributeModifier(modifierId, amount, operation));
	}
}
