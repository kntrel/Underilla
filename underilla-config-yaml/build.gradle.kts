plugins {
    `java-library`
}

description = "Platform-neutral YAML configuration support for Underilla."

dependencies {
    api(project(":underilla-core"))
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}
