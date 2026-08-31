plugins {
    base
    id("org.sonarqube") version "7.3.0.8198"
}

group = "com.kntrel.mc.underilla"
version = "2.3.4"
description = "Generate vanilla caves in custom worlds."

extra["mainMinecraftVersion"] = "1.21.11"
extra["supportedMinecraftVersions"] = "1.21.5 - 26.1.2"
extra["voidWorldGeneratorVersion"] = "1.3.12"
extra["chunkyVersion"] = "1.4.55"

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://jitpack.io")
        maven("https://repo.codemc.io/repository/maven-public/")
    }
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            withJavadocJar()
            withSourcesJar()
        }
    }
}

tasks.named("assemble") {
    dependsOn(":underilla-core:assemble", ":underilla-paper:assemble")
}

tasks.named("check") {
    dependsOn(":underilla-core:check", ":underilla-paper:check")
}

tasks.register("echoVersion") {
    doLast {
        println(project.version)
    }
}

tasks.register("echoReleaseName") {
    doLast {
        println("${project.version} [${extra["supportedMinecraftVersions"]}]")
    }
}

sonar {
    properties {
        property("sonar.projectKey", "Underilla")
        property("sonar.projectName", "Underilla")
        property("sonar.host.url", "https://mvndisonar.formiko.fr")
    }
}
