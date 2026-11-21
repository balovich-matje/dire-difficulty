// ONE node script for every Fabric node. Version conditionals here are plain Kotlin on
// `sc.current.parsed`; `//?` only works in source files, never in build scripts or JSON.
plugins {
	// fabric-loom on 26.x (unobfuscated).
	id("dev.kikugie.loom-back-compat")
	// Modrinth uploads, one version per node. Dry run unless `-PpublishLive=true`.
	id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

val modId: String = sc.properties["mod.id"]
val modVersion: String = sc.properties["mod.version"]
version = "$modVersion+${sc.current.version}"
group = sc.properties["mod.group"] as String
base.archivesName = modId

val requiredJava: JavaVersion = JavaVersion.VERSION_25

repositories {
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
		runDirectory = rootProject.file("run")
		jvmArguments.add("-Dmixin.debug.export=true")
	}
}

dependencies {
	fun fapi(vararg modules: String) {
		for (it in modules) modCompileOnly(fabricApi.module(it, sc.properties["deps.fabric_api"]))
	}

	minecraft("com.mojang:minecraft:${sc.current.version}")
	loomx.applyMojangMappings()
	modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

	fapi(
		"fabric-data-attachment-api-v1", // attachment.v1        — the elite state store
		"fabric-networking-api-v1", // networking.v1        — EliteStatePayload
		"fabric-entity-events-v1", // entity.event.v1      — tracking / death hooks
		"fabric-lifecycle-events-v1", // event.lifecycle.v1   — tickers, ENTITY_LOAD
		"fabric-command-api-v2", // command.v2           — /dire
		"fabric-rendering-v1", // client.rendering.v1  — render-state data, HUD
	)
	modLocalRuntime("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"] as String}")

	val modmenu: String = sc.properties["deps.modmenu"]
	val cloth: String = sc.properties["deps.cloth_config"]
	modCompileOnly("maven.modrinth:modmenu:$modmenu")
	modLocalRuntime("maven.modrinth:modmenu:$modmenu")
	modCompileOnly("maven.modrinth:cloth-config:$cloth")
	modLocalRuntime("maven.modrinth:cloth-config:$cloth")
}

java {
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
	toolchain {
		languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = requiredJava.majorVersion.toInt()
	options.encoding = "UTF-8"
}

val javaFloor: String = sc.properties["mod.java_floor"]
val metadataProps: Map<String, String> = mapOf(
	"id" to modId,
	"name" to sc.properties["mod.name"],
	"version" to version.toString(),
	"minecraft" to sc.properties["mod.mc_compat"],
	"loader" to sc.properties["mod.loader_floor"],
	"java_floor" to javaFloor,
)
val mixinJava = "JAVA_$javaFloor"

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

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds the mod jar and collects it into build/libs/<mod version>/"
		inputs.property("version", modVersion)
		from(loomx.modJar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
		into(rootProject.layout.buildDirectory.file("libs/$modVersion"))
	}
}

tasks.withType<JavaExec>().matching { it.name == "runServer" }.configureEach {
	standardInput = System.`in`
}

// ---------------------------------------------------------------- publishing (Modrinth)
// Same scheme as the sibling repos: bare version on the newest node, `+<node>` below it,
// changelog at changelogs/<version>.md with the `# H1` as the version name.

val nodeKey: String = sc.current.project.substringBeforeLast('-')
val isNewestNode: Boolean = sc.versions.none { it.parsed > sc.current.parsed }
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
	?.asList().orEmpty().map { it.toString() }
val modrinthVersion: String = if (isNewestNode) modVersion else "$modVersion+$nodeKey"
val modrinthProjectId: String? = sc.properties.rawOrNull("mod", "modrinth_id")?.toString()
val changelogPath = "changelogs/$modVersion.md"
val changelogText: Provider<String> = providers
	.fileContents(rootProject.layout.projectDirectory.file(changelogPath))
	.asBytes.map { String(it, Charsets.UTF_8) }
	.orElse(providers.provider<String> { error("No release notes at $changelogPath — write them before publishing") })
val releaseTitle: Provider<String> = changelogText.map {
	it.trim().lineSequence().firstOrNull()?.takeIf { line -> line.startsWith("# ") }?.removePrefix("# ")?.trim().orEmpty()
}
val releaseNotes: Provider<String> = changelogText.map {
	val lines = it.trim().lines()
	(if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines).joinToString("\n").trim()
}
val modrinthDisplayName: Provider<String> = releaseTitle.map { title ->
	buildString {
		append(modVersion)
		if (title.isNotEmpty()) append(" — ").append(title)
		if (!isNewestNode) append(" (").append(nodeKey).append(')')
	}
}
val publishLive: Boolean = providers.gradleProperty("publishLive").map(String::toBoolean).getOrElse(false)
val modrinthNameMax = 64

publishMods {
	dryRun = !publishLive
	file = loomx.modJar.flatMap { it.archiveFile }
	version = modrinthVersion
	displayName = modrinthDisplayName.map { name ->
		require(!publishLive || name.length <= modrinthNameMax) {
			"Modrinth caps a version name at $modrinthNameMax characters; this one is ${name.length}: \"$name\""
		}
		name
	}
	changelog = releaseNotes
	type = STABLE
	modLoaders.add("fabric")

	modrinth {
		projectId = providers.provider {
			modrinthProjectId ?: error("`mod.modrinth_id` is not set in stonecutter.properties.toml")
		}
		accessToken = providers.environmentVariable("MODRINTH_TOKEN")
		minecraftVersions.addAll(
			providers.provider {
				compatibleVersions.ifEmpty { error("`mod.mc_releases` is not declared for node ${sc.current.project}") }
			},
		)
		featured = true
		requires("P7dR8mSH") // fabric-api
		optional("mOgUt4GM") // modmenu
		optional("9s6osm5g") // cloth-config
	}
}

tasks.register("printPublishMetadata") {
	group = "publishing"
	description = "Prints the Modrinth metadata this node would upload. Builds nothing, uploads nothing."
	val rows = listOf(
		"node" to sc.current.project,
		"minecraft (jar)" to sc.current.version,
		"project" to (modrinthProjectId ?: "UNSET"),
		"version_number" to modrinthVersion,
		"game_versions" to compatibleVersions.toString(),
		"loaders" to "[fabric]",
		"dependencies" to "required=[P7dR8mSH fabric-api] optional=[mOgUt4GM modmenu, 9s6osm5g cloth-config]",
		"changelog file" to changelogPath,
		"mode" to if (publishLive) "LIVE UPLOAD" else "dry run",
	)
	val name = modrinthDisplayName
	val notes = releaseNotes
	doLast {
		rows.forEach { (k, v) -> logger.lifecycle("%-16s %s".format(k, v)) }
		val rendered = name.get()
		logger.lifecycle("%-16s %s".format("name", rendered))
		logger.lifecycle("%-16s %d / %d".format("name length", rendered.length, modrinthNameMax))
		logger.lifecycle("--- changelog ---")
		logger.lifecycle(notes.get())
	}
}
