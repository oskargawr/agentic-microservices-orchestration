plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    // Temporal client dependency reserved for TH-200/TH-301 — not wired yet
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Library/bootstrap module for future Temporal workers; no bootJar required for TH-100
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
tasks.named<Jar>("jar") {
    enabled = true
}
