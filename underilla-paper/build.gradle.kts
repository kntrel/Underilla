plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    `maven-publish`
    signing
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
    id("com.modrinth.minotaur") version "2.+"
    id("org.jreleaser") version "1.24.0"
}

description = "Paper plugin that generates vanilla caves in custom worlds."

val mainMinecraftVersion = rootProject.extra["mainMinecraftVersion"] as String
val supportedMinecraftVersions = rootProject.extra["supportedMinecraftVersions"] as String
val voidWorldGeneratorVersion = rootProject.extra["voidWorldGeneratorVersion"] as String
val chunkyVersion = rootProject.extra["chunkyVersion"] as String

dependencies {
    implementation(project(":underilla-core"))

    paperweight.paperDevBundle("$mainMinecraftVersion-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-text-serializer-ansi:4.17.0")

    implementation("com.github.FormikoLudo:Utils:0.0.9")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly("fr.formiko.mc.voidworldgenerator:voidworldgenerator:$voidWorldGeneratorVersion")
    compileOnly("org.popcraft:chunky-common:$chunkyVersion")
}

tasks {
    shadowJar {
        minimize()
        val prefix = "${project.group}.lib"
        sequenceOf(
            "co.aikar",
            "org.bstats",
            "fr.formiko.utils",
        ).forEach { pkg ->
            relocate(pkg, "$prefix.$pkg")
        }
        archiveFileName.set("Underilla-${project.version}.jar")
        exclude("javax/**")
        exclude("assets/**")
        exclude("com/google/**")
        exclude("org/checkerframework/**")
        exclude("org/apache/**")
    }

    assemble {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf(
            "name" to "Underilla",
            "version" to project.version,
            "description" to project.description,
            "apiVersion" to "1.21.5",
            "group" to project.group,
            "voidWorldGeneratorVersion" to voidWorldGeneratorVersion,
            "chunkyVersion" to chunkyVersion,
        )
        inputs.properties(props)
        filesMatching(listOf("paper-plugin.yml", "config.yml")) {
            expand(props)
        }
    }

    runServer {
        minecraftVersion(mainMinecraftVersion)
    }
}

val extractChangelog = tasks.register("extractChangelog") {
    group = "documentation"
    description = "Extracts the changelog for the current project version."

    val changelog = project.objects.property(String::class)
    outputs.upToDateWhen { false }

    doLast {
        val changelogFile = rootProject.file("CHANGELOG.md")
        if (!changelogFile.exists()) {
            changelog.set("No changelog found.")
            return@doLast
        }

        val entries = mutableListOf<String>()
        var foundVersion = false
        for (line in changelogFile.readLines()) {
            when {
                line.trim().equals("# ${project.version}", ignoreCase = true) -> {
                    foundVersion = true
                    entries.add(line)
                }
                foundVersion && line.trim().startsWith("# ") -> break
                foundVersion -> entries.add(line)
            }
        }
        changelog.set(entries.joinToString("\n").trim().ifEmpty { "Update to ${project.version}." })
    }

    extensions.add("changelog", changelog)
}

tasks.register("echoLatestVersionChangelog") {
    group = "documentation"
    dependsOn(extractChangelog)
    doLast {
        println((extractChangelog.get().extensions.getByName("changelog") as Property<String>).get())
    }
}

val versionString = project.version.toString()
val isRelease = !versionString.contains("SNAPSHOT")

hangarPublish {
    publications.register("plugin") {
        version.set(versionString)
        channel.set(if (isRelease) "Release" else "Snapshot")
        id.set("Underilla")
        apiKey.set(System.getenv("HANGAR_API_TOKEN"))
        changelog.set(extractChangelog.map {
            (it.extensions.getByName("changelog") as Property<String>).get()
        })
        platforms {
            register(io.papermc.hangarpublishplugin.model.Platforms.PAPER) {
                url = "https://github.com/kntrel/underilla/releases/download/$versionString/Underilla-$versionString.jar"
                platformVersions.set(supportedMinecraftVersions.replace(" ", "").split(","))
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "underilla-paper"

            pom {
                name.set("underilla-paper")
                description.set(project.description)
                url.set("https://github.com/kntrel/underilla")
                inceptionYear.set("2023")
                licenses {
                    license {
                        name.set("MIT license")
                        url.set("https://github.com/kntrel/underilla/blob/main/LICENSE.md")
                    }
                }
                developers {
                    developer {
                        id.set("kntrel")
                        name.set("kntrel")
                        email.set("me@kntrel.com")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:kntrel/underilla.git")
                    developerConnection.set("scm:git:ssh:git@github.com:kntrel/underilla.git")
                    url.set("https://github.com/kntrel/underilla")
                }
            }
        }
    }
    repositories {
        maven {
            name = "PreDeploy"
            url = uri(rootProject.layout.buildDirectory.dir("pre-deploy"))
        }
    }
}

jreleaser {
    project {
        name.set("Underilla")
        copyright.set("kntrel")
        description.set(project.description)
        website.set("https://github.com/kntrel/underilla")
    }

    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active.set(org.jreleaser.model.Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    username.set(findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
                    password.set(findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
                    stagingRepository(rootProject.layout.buildDirectory.dir("pre-deploy").get().asFile.absolutePath)
                    applyMavenCentralRules = false
                }
            }
        }
    }

    release {
        github {
            enabled.set(false)
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}
