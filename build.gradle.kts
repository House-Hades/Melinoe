import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
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
    
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    
    implementation("net.kyori:adventure-platform-fabric:6.9.0")
    include("net.kyori:adventure-platform-fabric:6.9.0")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")
    
    implementation("dev.firstdark.discordrpc:discord-rpc:1.0.4")
    include("dev.firstdark.discordrpc:discord-rpc:1.0.4")

    property("commodore_version").let {
        implementation("com.github.stivais:Commodore:$it")
        include("com.github.stivais:Commodore:$it")
    }

    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    property("minecraft_lwjgl_version").let { lwjglVersion ->
        implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
        include("org.lwjgl:lwjgl-nanovg:$lwjglVersion")

        listOf("windows", "linux", "macos", "macos-arm64").forEach { os ->
            implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
            include("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
        }
    }
    
    compileOnly("maven.modrinth:iris:${property("iris")}")
}

loom {
    accessWidenerPath = rootProject.file("src/main/resources/melinoe.accesswidener")
    runConfigs.named("client") {
        isIdeConfigGenerated = true
        vmArgs(
            "-Dmixin.debug.export=true",
            "-Ddevauth.enabled=true",
            "-Ddevauth.account=main",
            "-XX:+AllowEnhancedClassRedefinition"
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
            jvmTarget = JvmTarget.JVM_25
            freeCompilerArgs.add("-Xlambdas=class") //Commodore
        }
    }

    compileJava {
        sourceCompatibility = "25"
        targetCompatibility = "25"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
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

