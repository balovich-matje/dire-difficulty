package com.escalation.platform;

import java.nio.file.Path;

/**
 * THE SEAM — loader services. Everything the mod asks the mod loader itself, as opposed to
 * the game: where the config directory is, and whether another mod is present.
 *
 * <p>{@code com.escalation.platform} is the <b>only</b> package that may name
 * {@code FabricLoader}, {@code AttachmentTarget}, {@code AttachmentType},
 * {@code AttachmentRegistry}, {@code AttachmentSyncPredicate}, {@code PayloadTypeRegistry},
 * {@code ServerPlayNetworking}, {@code ClientPlayNetworking}, or any NeoForge/Forge API. A
 * call site anywhere else is drift, and <b>a grep is the review gate</b> — the same gate both
 * sibling repos use ({@code docs/DESIGN.md} §6.6).
 *
 * <p>Two more seams join this one as the systems land, each the same shape (interface +
 * {@code static final INSTANCE} + package-private per-loader impl): {@code EliteStore} for
 * all attached entity state, and {@code Net} for clientbound payload registration and sends.
 *
 * <p><b>Two facts to build around, both already paid for in the sibling repos.</b>
 *
 * <ul>
 * <li>{@code src/main} cannot reach client-only API — measured, not assumed. The
 *     {@code ClientPlayNetworking} receiver for the elite-state payload will live in
 *     {@code client/EscalationClient}. Do not "complete" {@code Net} with a client method.</li>
 * <li>At port time {@link #INSTANCE} becomes a {@code fabric}/{@code neoforge}/{@code forge}
 *     <b>block-form</b> chain that repeats the whole field declaration. The maintained
 *     multiloader template's one-line inline {@code elif} cannot chain — measured in Skill
 *     Proficiencies' Stage 6, a second one fails with {@code Unmatched scope closer}.</li>
 * </ul>
 *
 * <p>No directive token is written out anywhere in this file on purpose: Stonecutter scans
 * javadoc like any other text, so a sample directive in a comment is a live directive.
 */
public interface Platform {
	Platform INSTANCE = new FabricPlatform();

	/** The instance config directory. The mod writes its Gson config file into it. */
	Path configDir();

	/** True if a mod with this id is loaded. Used for the optional Cloth Config UI. */
	boolean isModLoaded(String id);
}
