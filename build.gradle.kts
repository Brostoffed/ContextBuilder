plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.Brostoffed"
version = "1.0.3-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform.defaultRepositories()
}

// IntelliJ Platform configuration (2.x)
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("252")
            untilBuild.set("252.*")
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks {
    // Disable searchable options generation (flaky headless)
    buildSearchableOptions {
        enabled = false
    }
    // Work around missing coroutines debug agent in some platform builds
    runIde {
        jvmArgs("-Dkotlinx.coroutines.debug=off")
    }
}

dependencies {
    // Prefer a local IDE if available, else fall back to 2025.2 platform
    intellijPlatform {
        val envIde = System.getenv("IDEA_LOCAL_PATH")
        val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)
        val defaultMacCe = "/Applications/IntelliJ IDEA CE.app"
        val autoLocalPath = when {
            !envIde.isNullOrBlank() -> envIde
            isMac && file(defaultMacCe).exists() -> defaultMacCe
            else -> null
        }
        if (autoLocalPath != null) local(autoLocalPath) else intellijIdeaCommunity("2025.2")
        bundledPlugins("com.intellij.java")
    }
    implementation("com.knuddels:jtokkit:1.1.0")
}

// Provide a coroutines debug agent at the path some IDE builds expect
configurations.register("coroutinesAgent")
dependencies.add("coroutinesAgent", "org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.9.0")

val prepareCoroutinesAgent by tasks.registering(Copy::class) {
    val agent = configurations.named("coroutinesAgent").get().elements.map { it.first().asFile }
    from(agent)
    rename { "coroutines-javaagent.jar" }
    into(layout.buildDirectory)
}

tasks.named("runIde").configure { dependsOn(prepareCoroutinesAgent) }
tasks.named("processResources").configure { dependsOn(prepareCoroutinesAgent) }
tasks.named("verifyPluginProjectConfiguration").configure { dependsOn(prepareCoroutinesAgent) }
tasks.named("processTestResources").configure { dependsOn(prepareCoroutinesAgent) }
