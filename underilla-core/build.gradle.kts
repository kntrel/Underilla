plugins {
    `java-library`
}

description = "Platform-neutral world reading and terrain merging for Underilla."

dependencies {
    api("com.github.kntrel:KntNBT:v2.3.0")
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("info.picocli:picocli:4.7.7")    // Used by inspector
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("inspect") {
    group = "verification"
    description = "Render the synthetic world-generation slice without running tests."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.kntrel.mc.underilla.core.inspector.Inspector")
    workingDir = project.projectDir
}
