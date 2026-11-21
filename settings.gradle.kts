// Stonecutter multi-version workspace (2026-11-21), same shape as Skill Proficiencies and
// Archetypes: shared src/, one node per target under versions/<mc>-fabric/, version diffs as
// `//?` comments. Binding conventions: ../specialities/docs/MULTIVERSION-CONVENTIONS.md.
pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "FabricMC" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGieReleases" }
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.9.7"
	id("dev.kikugie.loom-back-compat") version "0.4.1"
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
	create(rootProject) {
		fun match(project: String, vararg loaders: String, version: String = project) {
			for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
		}
		match("26.3", "fabric")
		match("26.2", "fabric")
		vcsVersion = "26.2-fabric"
	}
}

rootProject.name = "dire_difficulty"
