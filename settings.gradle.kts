pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "FabricMC" }
	}

	// Single source of version truth for the toolchain: gradle.properties. The plugins
	// block below cannot read `providers.gradleProperty(...)` (restricted scope), so the
	// version is resolved here through the `by settings` delegate instead of being
	// duplicated as a literal in build.gradle.kts.
	val loom_version: String by settings

	plugins {
		id("net.fabricmc.fabric-loom") version loom_version
	}
}

plugins {
	// Provisions the JDK the toolchain asks for (25) if it is not already installed.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// SINGLE PROJECT ON PURPOSE. Skill Proficiencies and Archetypes are both Stonecutter
// multi-version workspaces; this repo is deliberately NOT one yet. It targets 26.2 only,
// and the port to the same seven targets happens later — at which point this file grows a
// `stonecutter { create(rootProject) { … } }` block and `build.gradle.kts` splits into
// `build.fabric.gradle.kts` / `build.neoforge.gradle.kts` / `build.forge.gradle.kts`.
// Everything in `src/` is written to survive that move (docs/DESIGN.md §6.6): Java 17 as
// the shared-code ceiling, the `platform/` seam, no `//?` directives anywhere yet.

// Matches the mod id. Never change it — the id is permanent (docs/DESIGN.md §7.5).
rootProject.name = "dire_difficulty"
