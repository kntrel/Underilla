plugins {
    `java-library`
    `maven-publish`
    signing
}

description = "Platform-neutral world reading and terrain merging for Underilla."

dependencies {
    api("com.github.HydrolienF:KntNBT:2.2.2")
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "underilla-core"

            pom {
                name.set("underilla-core")
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

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}
