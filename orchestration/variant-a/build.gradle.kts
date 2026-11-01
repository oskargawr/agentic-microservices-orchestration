plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":contracts"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.temporal:temporal-sdk:1.40.0")

    testImplementation("io.temporal:temporal-testing:1.40.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.postgresql:postgresql")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
tasks.named<Jar>("jar") {
    enabled = true
}

tasks.named<Test>("test") {
    // Happy-path IT launches service bootJars in separate JVMs
    dependsOn(
        ":services:inventory:bootJar",
        ":services:payment:bootJar",
        ":services:shipping:bootJar",
    )
    // Resolve boot jars relative to repo root
    workingDir = rootProject.projectDir
}
