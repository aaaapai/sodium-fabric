import net.fabricmc.loom.task.AbstractRemapJarTask

plugins {
    id("java")
    id("idea")
    id("fabric-loom") version "1.7.1"
}

val MINECRAFT_VERSION: String by rootProject.extra
val FABRIC_LOADER_VERSION: String by rootProject.extra
val FABRIC_API_VERSION: String by rootProject.extra

// This trick hides common tasks in the IDEA list.
tasks.configureEach {
    group = null
}

dependencies {
    minecraft(group = "com.mojang", name = "minecraft", version = MINECRAFT_VERSION)
    mappings(loom.officialMojangMappings())
    compileOnly("io.github.llamalad7:mixinextras-common:0.3.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.3.5")
    compileOnly("net.fabricmc:sponge-mixin:0.13.2+mixin.0.8.5")

    fun addDependentFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        modCompileOnly(module)
    }

    addDependentFabricModule("fabric-api-base")
    addDependentFabricModule("fabric-block-view-api-v2")
    addDependentFabricModule("fabric-renderer-api-v1")
    addDependentFabricModule("fabric-rendering-data-attachment-v1")

    modCompileOnly("net.fabricmc.fabric-api:fabric-renderer-api-v1:3.2.9+1172e897d7")
    implementation(group = "com.lodborg", name = "interval-tree", version = "1.0.0")
}

tasks.withType<AbstractRemapJarTask>().configureEach {
    targetNamespace = "named"
}

sourceSets {
    val main = getByName("main")
    val api = create("api")
    val workarounds = create("workarounds")
    val desktop = create("desktop")

    api.apply {
        java {
            compileClasspath += main.compileClasspath
        }
    }

    workarounds.apply {
        java {
            compileClasspath += main.compileClasspath
        }
    }

    desktop.apply {
        java {
            srcDir("src/desktop/java")
        }
    }

    main.apply {
        java {
            compileClasspath += api.output
            compileClasspath += workarounds.output
            runtimeClasspath += api.output
        }
    }
}

loom {
    mixin {
        defaultRefmapName = "sodium.refmap.json"
        useLegacyMixinAp = false
    }

    accessWidenerPath = file("src/main/resources/sodium.accesswidener")
}

tasks {
    getByName<JavaCompile>("compileDesktopJava") {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }

    jar {
        from(rootDir.resolve("LICENSE.md"))

        val api = sourceSets.getByName("api")
        from(api.output.classesDirs)
        from(api.output.resourcesDir)

        manifest.attributes["Main-Class"] = "net.caffeinemc.mods.sodium.desktop.LaunchWarn"
    }
}
