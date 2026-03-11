import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

group = property("maven_group") as String
version = property("mod_version") as String

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.terraformersmc.com/")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.firstdark.dev/releases")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    modImplementation("net.kyori:adventure-platform-fabric:6.7.0")
    include("net.kyori:adventure-platform-fabric:6.7.0")

    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")
    
    implementation("dev.firstdark.discordrpc:discord-rpc:1.0.4")
    include("dev.firstdark.discordrpc:discord-rpc:1.0.4")

    property("commodore_version").let {
        implementation("com.github.stivais:Commodore:$it")
        include("com.github.stivais:Commodore:$it")
    }

    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    property("minecraft_lwjgl_version").let { lwjglVersion ->
        modImplementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
        include("org.lwjgl:lwjgl-nanovg:$lwjglVersion")

        listOf("windows", "linux", "macos", "macos-arm64").forEach { os ->
            modImplementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
            include("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
        }
    }

    modCompileOnly("maven.modrinth:iris:${property("iris")}")
}

loom {
    runConfigs.named("client") {
        isIdeConfigGenerated = true
        vmArgs.addAll(
            arrayOf(
                "-Dmixin.debug.export=true",
                "-Ddevauth.enabled=true",
                "-Ddevauth.account=main",
                "-XX:+AllowEnhancedClassRedefinition"
            )
        )
    }

    runConfigs.named("server") {
        isIdeConfigGenerated = false
    }
}

tasks {
    processResources {
        val expandProps = mapOf(
            "mod_version" to project.property("mod_version") as String,
            "minecraft_version" to project.property("minecraft_version") as String,
            "loader_version" to project.property("loader_version") as String,
            "mod_id" to project.property("mod_id") as String,
            "mod_name" to project.property("mod_name") as String,
            "mod_description" to project.property("mod_description") as String,
            "fabric_api_version" to project.property("fabric_api_version") as String,
            "fabric_kotlin_version" to project.property("fabric_kotlin_version") as String
        )
        
        inputs.properties(expandProps)
        
        filesMatching("fabric.mod.json") {
            expand(expandProps)
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            freeCompilerArgs.add("-Xlambdas=class") //Commodore
        }
    }

    compileJava {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "me.melinoe"
            artifactId = "Melinoe"
            version = version
            from(components["java"])
        }
    }
}

