dependencies {
    implementation(project(":contracts"))
    implementation("org.postgresql:postgresql")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
