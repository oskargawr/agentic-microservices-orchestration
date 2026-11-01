plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":orchestration:variant-a"))
    implementation(project(":contracts"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("io.temporal:temporal-sdk:1.40.0")
}

tasks.named<Jar>("jar") {
    enabled = true
}
