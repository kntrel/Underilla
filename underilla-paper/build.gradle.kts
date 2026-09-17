plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.22"
}

description = "Paper plugin that generates vanilla caves in custom worlds."

val mainMinecraftVersion = rootProject.extra["mainMinecraftVersion"] as String
val voidWorldGeneratorVersion = rootProject.extra["voidWorldGeneratorVersion"] as String
val chunkyVersion = rootProject.extra["chunkyVersion"] as String

dependencies {
    implementation(project(":underilla-core"))

    paperweight.paperDevBundle("$mainMinecraftVersion.build.+")
    compileOnly("net.kyori:adventure-text-serializer-ansi:4.17.0")

    implementation("com.github.FormikoLudo:Utils:0.0.9")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly("fr.formiko.mc.voidworldgenerator:voidworldgenerator:$voidWorldGeneratorVersion")
    compileOnly("org.popcraft:chunky-common:$chunkyVersion")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.kyori:adventure-text-serializer-ansi:4.17.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

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
}
