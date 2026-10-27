plugins {
    `java-library`
}

description = "Platform-neutral YAML configuration support for Underilla."

dependencies {
    api(project(":underilla-core"))
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    compileOnly("org.yaml:snakeyaml:2.7")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("org.yaml:snakeyaml:2.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}

// Keep loader defaults distinct from the hosting plugin's root config.yml.
tasks.processResources {
    from("src/main/resources/config.yml") {
        into("com/kntrel/mc/underilla/config/yaml")
    }
}
