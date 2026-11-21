// Controller = the root build script. Active node is the VCS reset point (26.2, as in the
// sibling repos): src/ on disk is always in 26.2 form; 26.3 compiles from generated sources.
plugins {
	id("dev.kikugie.stonecutter")
}

stonecutter active "26.2-fabric"

stonecutter parameters {
	val (version, loader) = current.project.split('-', limit = 2)
	properties {
		tags(version, loader)
	}
	constants {
		match(loader, "fabric", "neoforge", "forge")
	}
	dependencies["fapi"] = properties.getOrNull<String>("deps.fabric_api") ?: "0"
	replacements {
		string(current.parsed >= "26.3") {
			replace(".hurtMarked", ".syncVelocity")
			replace(
				"placeItemBackInInventory(stack)",
				"placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY)",
			)
		}
	}
}

stonecutter tasks {
	order("publishModrinth")
}
