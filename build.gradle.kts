plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.12.0"
}

group = "com.weakviord"
version = "1.0.5"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.1")
    type.set("IC")
    plugins.set(listOf(
        "java",
        "platform-images",
        "properties",
        "yaml"
    ))

    sandboxDir.set("${project.buildDir}/idea-sandbox")
    updateSinceUntilBuild.set(false)
    sameSinceUntilBuild.set(true)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("243.*")
        pluginDescription.set("""
            File Tagger plugin for JetBrains IDEs.
            
            Features:
            - Add, edit, and remove tags for files and directories
            - Customize tag colors with support for transparency
            - Tags are displayed in different colors in the project view
            - Easy tag management through context menu
        """.trimIndent())
        changeNotes.set("""
            Initial release:
            - Basic tag management functionality
            - Colorful tag display
            - Context menu integration
            - Support for all JetBrains IDEs
        """.trimIndent())
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN") ?: "")
        privateKey.set(System.getenv("PRIVATE_KEY") ?: "")
        password.set(System.getenv("PRIVATE_KEY_PASSWORD") ?: "")
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN") ?: "perm-WmhvbnFnaV9XZW4=.OTItMTE2MDk=.pQT9H0Ebvigip9BJQpjXZkBy3S4AW5")
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    register<Exec>("verifyPluginLocally") {
        dependsOn("buildPlugin")
        commandLine("./verify-plugin.sh")
        
        doFirst {
            mkdir("build/reports/verification")
        }
    }
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java")
        }
        resources {
            srcDirs("src/main/resources")
        }
    }
} 