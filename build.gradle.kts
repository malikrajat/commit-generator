import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "com.rajatmalik"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        webstorm(providers.gradleProperty("platformVersion").get())
        bundledPlugin("Git4Idea")
    }

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "241"
            untilBuild = "263.*"
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.WebStorm, "2024.1")
            create(IntelliJPlatformType.WebStorm, "2025.1")
            create(IntelliJPlatformType.WebStorm, "2026.1")
            create(IntelliJPlatformType.WebStorm, "2026.3")
            create(IntelliJPlatformType.IntellijIdea, "2026.3")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion").map { version ->
            listOf(version.substringAfter('-', "default").substringBefore('.').ifEmpty { "default" })
        }
    }
}
