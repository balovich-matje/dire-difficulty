// SCAFFOLD BUILD — single project, Minecraft 26.2 Fabric only.
//
// This is deliberately NOT a Stonecutter workspace (see settings.gradle.kts). It is the
// pre-port shape of the two sibling repos: one node, plain fabric-loom, split environment
// source sets. The port to the same seven targets is a later stage, so nothing here may
// contain a `//?` directive — Stonecutter does not process build scripts at all, and
// version-conditional build logic is a plain Kotlin `if` when the time comes.

plugins {
	// Version comes from `loom_version` in gradle.properties, resolved in settings.gradle.kts.
	// 26.2 is unobfuscated, so this is the plain loom, not `fabric-loom-remap`.
	id("net.fabricmc.fabric-loom")
	id("maven-publish")
}

val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modVersion = property("mod_version") as String

version = "$modVersion+${property("minecraft_version")}"
group = property("maven_group") as String
base.archivesName = modId

// Java 25 on 26.2, matching the sibling repos' newest node. NOTE the standing rule that
// outlives this file: the SHARED code ceiling is Java 17 (docs/DESIGN.md §6.6), because the
// port's 1.20.1 node compiles the same tree at source level 17. Records are fine; pattern
// matching `switch`, sealed types and `Math.clamp` are not. Writing to that ceiling now
// costs nothing and saves a sweep later — this `release = 25` will NOT catch a violation.
val targetJava: JavaVersion = JavaVersion.VERSION_25

repositories {
	// Mod Menu and Cloth Config only. Scoped to `maven.modrinth` so it never intercepts
	// another lookup.
	exclusiveContent {
		forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
		filter { includeGroup("maven.modrinth") }
	}
}

loom {
	splitEnvironmentSourceSets()

	mods {
		create(modId) {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets["client"])
		}
	}

	runConfigs.all {
		preferGradleTask = true
		// Dumps transformed classes to run/.mixin.out — the only way to MEASURE mixin
		// ordering against Skill Proficiencies and Archetypes, which docs/DESIGN.md §6.4
		// requires for Guarantee A rather than trusting the priority documentation.
		jvmArguments.add("-Dmixin.debug.export=true")
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${property("minecraft_version")}")
	// NO `mappings` line and no yarn, ever. 26.2 ships unobfuscated with Mojang official
	// names, so loom needs nothing here.
	implementation("net.fabricmc:fabric-loader:${property("loader_version")}")

	/** Compile-classpath-only Fabric API modules, one entry per package `src/` imports. */
	fun fapi(vararg modules: String) {
		for (module in modules) {
			compileOnly(fabricApi.module(module, property("fabric_api_version") as String))
		}
	}

	// The modules docs/DESIGN.md §6 commits to. Compile classpath ONLY — the dev runtime
	// gets the umbrella below instead, because the shipped fabric.mod.json declares
	// `depends: fabric-api` and only the umbrella jar carries that mod id. Per-module jars
	// declare their own ids, so a modules-only dev run dies at loader resolution. The
	// modules must NOT also be on the runtime classpath or each one duplicates its copy
	// nested inside the umbrella.
	fapi(
		"fabric-data-attachment-api-v1", // attachment.v1        — the elite state store
		"fabric-networking-api-v1", // networking.v1        — EliteStatePayload
		"fabric-entity-events-v1", // entity.event.v1      — tracking / death hooks
		"fabric-lifecycle-events-v1", // event.lifecycle.v1   — END_SERVER_TICK (The Hunt)
		"fabric-command-api-v2", // command.v2           — /dire debug commands
		"fabric-rendering-v1", // client.rendering.v1  — the elite glow layer
	)

	// The whole thing, dev runs only. Production never sees it: users install fabric-api
	// themselves, and a headless smoke server drops the same jar into mods/.
	localRuntime("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

	// Optional config UI. Compiled against and present in the dev client, but only SOFT
	// dependencies at runtime ("suggests" in fabric.mod.json, `isModLoaded` guards behind
	// the platform seam). `modCompileOnly` + `modLocalRuntime` rather than
	// `modImplementation` so neither can ever leak into the published jar's dependency
	// metadata — the Gson config has to work with both absent.
	compileOnly("maven.modrinth:modmenu:${property("modmenu_version")}")
	localRuntime("maven.modrinth:modmenu:${property("modmenu_version")}")
	compileOnly("maven.modrinth:cloth-config:${property("cloth_config_version")}")
	localRuntime("maven.modrinth:cloth-config:${property("cloth_config_version")}")
}

java {
	// Loom attaches this to remapSourcesJar and to `build` automatically.
	withSourcesJar()
	sourceCompatibility = targetJava
	targetCompatibility = targetJava

	// Deliberately no `vendor = ADOPTIUM`: an exact-version local JDK is preferred, which
	// is the Homebrew openjdk@25 the other two repos build with.
	toolchain {
		languageVersion = JavaLanguageVersion.of(targetJava.majorVersion)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = targetJava.majorVersion.toInt()
	options.encoding = "UTF-8"
}

// fabric.mod.json lives in `main`, but dire_difficulty.client.mixins.json lives in `client` —
// with split source sets there are TWO ProcessResources tasks and both need the
// substitutions, or the client mixin config ships a literal `${java}`.
val metadataProps: Map<String, String> = mapOf(
	"id" to modId,
	"name" to modName,
	// `0.1.0+26.2`, not bare `0.1.0` — the jar has to name its game version.
	"version" to version.toString(),
	"minecraft" to property("minecraft_compat") as String,
	"loader" to property("loader_floor") as String,
	// fabric.mod.json wants `>=25`; the mixin configs want `JAVA_25`.
	"java_floor" to property("java_floor") as String,
)

// Resolved HERE, at script scope. Inside `configureEach` the `property(...)` lookup would
// resolve against the TASK rather than the project and fail with "unknown property".
val mixinJava = "JAVA_${property("java_floor")}"

tasks.withType<ProcessResources>().configureEach {
	metadataProps.forEach { (k, v) -> inputs.property(k, v) }
	inputs.property("mixinJava", mixinJava)

	filesMatching("fabric.mod.json") { expand(metadataProps) }
	filesMatching("*.mixins.json") { expand("java" to mixinJava) }
}

tasks {
	jar {
		inputs.property("modId", modId)
		from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
	}

	// Same shape the sibling repos collect their per-node jars with, so the release
	// tooling added later has the same target directory to reach for.
	//
	// 26.2 is UNOBFUSCATED, so loom's remap pipeline is not in play and there is no
	// `remapJar`/`remapSourcesJar` task at all — the shippable artifacts are plain `jar`
	// and `sourcesJar`. Resolved by name rather than hardcoded, because the port's legacy
	// nodes DO get the remap tasks and this then keeps working unchanged.
	val jarTaskName = if (names.contains("remapJar")) "remapJar" else "jar"
	val sourcesJarTaskName = if (names.contains("remapSourcesJar")) "remapSourcesJar" else "sourcesJar"

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds the mod jar and collects it into build/libs/<mod version>/"
		inputs.property("version", modVersion)
		from(
			named<Jar>(jarTaskName).flatMap { it.archiveFile },
			named<Jar>(sourcesJarTaskName).flatMap { it.archiveFile },
		)
		into(rootProject.layout.buildDirectory.file("libs/$modVersion"))
	}
}

// Lets `echo stop | ./gradlew runServer` shut a headless smoke server down cleanly instead
// of it being killed. Loom does not wire stdin through by default.
tasks.withType<JavaExec>().matching { it.name == "runServer" }.configureEach {
	standardInput = System.`in`
}

publishing {
	publications.create<MavenPublication>("mavenJava") {
		from(components["java"])
		groupId = group.toString()
		artifactId = modId
		// Bare version, no `+26.2`: a consumer repo pins one coordinate.
		version = modVersion
	}
}
