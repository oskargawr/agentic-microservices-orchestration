dependencies {
    implementation(project(":contracts"))
    implementation("org.postgresql:postgresql")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * Reuse canonical Flyway SQL from services (no duplicated DDL). Distinct classpath
 * locations so OracleJdbcIT can migrate each schema against a fresh Postgres.
 */
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
