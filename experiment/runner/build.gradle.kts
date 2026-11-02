dependencies {
    implementation(project(":contracts"))
    implementation(project(":experiment:oracle"))
    implementation(project(":orchestration:variant-a"))

    implementation("io.temporal:temporal-sdk:1.40.0")
    implementation("org.postgresql:postgresql")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.zaxxer:HikariCP")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "pl.edu.pw.thesis.experiment.runner.cli.RunnerMain"
    }
}

tasks.named<Copy>("processTestResources") {
    from(rootProject.file("services/inventory/src/main/resources/db/migration")) {
        into("db/migration/inventory")
    }
    from(rootProject.file("services/payment/src/main/resources/db/migration")) {
        into("db/migration/payment")
    }
    from(rootProject.file("services/shipping/src/main/resources/db/migration")) {
        into("db/migration/shipping")
    }
}

tasks.named<Test>("test") {
    workingDir = rootProject.projectDir
}
