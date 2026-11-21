package com.diredifficulty.elite;

import com.diredifficulty.Tuning;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The three artefacts whose bonus is not a vanilla attribute: Bloodletter (lifesteal on
 * melee hits), Second Wind (cheats one death per cooldown, stored on the item) and Nighteye
 * Helm (night vision while worn). Attribute artefacts need nothing here.
 */
public final class ArtefactEffects {
	private static final String SECOND_WIND_READY_AT = "dire_difficulty_second_wind_ready_at";

	private ArtefactEffects() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken <= 0.0F || !(source.getEntity() instanceof Player attacker)
					|| source.getDirectEntity() != attacker) {
				return;
			}

			if (Artefact.of(attacker.getMainHandItem()) == Artefact.BLOODLETTER) {
				attacker.heal(damageTaken * Tuning.ARTEFACT_LIFESTEAL);
			}
		});

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !secondWind(entity));

		ServerTickEvents.END_SERVER_TICK.register(ArtefactEffects::tickNighteye);
	}

	/** True when Second Wind fired and the death is cancelled. */
	private static boolean secondWind(final LivingEntity entity) {
		ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);

		if (Artefact.of(chest) != Artefact.SECOND_WIND) {
			return false;
		}

		long now = entity.level().getGameTime();
		CustomData data = chest.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();

		if (now < tag.getLongOr(SECOND_WIND_READY_AT, 0L)) {
			return false;
		}

		tag.putLong(SECOND_WIND_READY_AT, now + Tuning.ARTEFACT_SECOND_WIND_COOLDOWN_TICKS);
		chest.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		entity.setHealth(Tuning.ARTEFACT_SECOND_WIND_HEALTH);
		entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
		entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
		entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TOTEM_USE,
				entity.getSoundSource(), 0.6F, 1.3F);
		return true;
	}

	private static void tickNighteye(final MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Artefact.of(player.getItemBySlot(EquipmentSlot.HEAD)) == Artefact.NIGHTEYE_HELM) {
				// 13 s, refreshed every 2 s: above vanilla's 10 s flicker threshold, gone soon
				// after the helm comes off.
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
			}
		}
	}
}
