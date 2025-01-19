plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.12.0"
}

group = "com.weakviord"
version = "1.0.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.1")
    type.set("GO")
    plugins.set(listOf())

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
            File Tagger plugin for GoLand.
            
            Features:
            - Add, edit, and remove tags for files and directories
            - Tags are displayed in different colors in the project view
            - Supports both light and dark themes
            - Easy tag management through context menu
        """.trimIndent())
        changeNotes.set("""
            Initial release:
            - Basic tag management functionality
            - Colorful tag display
            - Context menu integration
        """.trimIndent())
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN") ?: "")
        privateKey.set(System.getenv("PRIVATE_KEY") ?: "")
        password.set(System.getenv("PRIVATE_KEY_PASSWORD") ?: "")
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN") ?: "your token")
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