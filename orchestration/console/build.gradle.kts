plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":orchestration:variant-a"))
    implementation(project(":contracts"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.temporal:temporal-sdk:1.40.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.temporal:temporal-testing:1.40.0")
    testRuntimeOnly("org.postgresql:postgresql")
}

tasks.named<Jar>("jar") {
    enabled = true
}

tasks.named<Test>("test") {
    dependsOn(
        ":services:inventory:bootJar",
        ":services:payment:bootJar",
        ":services:shipping:bootJar",
    )
    workingDir = rootProject.projectDir
}
