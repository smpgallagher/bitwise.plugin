import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES
plugins {
    id("java")
    // Use the Kotlin JVM plugin to support writing the plugin in Kotlin
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    // The core IntelliJ Platform plugin
    id("org.jetbrains.intellij.platform")
}
//intellijPlatform { instrumentCode = true }
group = "com.bitwise.plugin"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
//    implementation("org.jetbrains.kotlin:analysis-api-for-ide:2.3.20")
    intellijPlatform {
        // 1. Define the IDE you are building against
        intellijIdea("2025.3")

        // 2. THIS FIXES THE RED TEXT: Add the Java and Kotlin "Modules"
        bundledPlugin("org.jetbrains.kotlin")

        bundledPlugin("com.intellij.java")
//        instrumentCode()
        // 3. Optional: Add the test framework if you're writing tests
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
//        instrumentationTools()
    }
}

intellijPlatform {
    instrumentCode.set(true)
    pluginConfiguration {
        id.set("com.bitwise.plugin")
        name.set("Bitwise Viewer")

        // Also apply this to other fields if they show red
        version.set("1.1.0")
        vendor.name.set("Bitwise")

        ideaVersion {
            sinceBuild.set("253")
//            untilBuild.set("271.*")
        }
    }
    pluginVerification {
        ides {
            // Option A: Test against the version you're currently building with
            select {
                // Verify against the 2025.3 platform line used by Android Studio 253
                types.add(IntelliJPlatformType.IntellijIdea)
                sinceBuild.set("253")
            }

            // Option B: Automatically test against a range of stable versions
//             recommended()
        }
        failureLevel.set(listOf(
            INTERNAL_API_USAGES,
            OVERRIDE_ONLY_API_USAGES
        ))
    }

    publishing {
        token.set(providers.environmentVariable("JB_MARKETPLACE_TOKEN"))
    }
}
kotlin {
    jvmToolchain(21)
}
