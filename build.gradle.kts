plugins {
    kotlin("jvm") version "2.1.0"
    id("org.openjfx.javafxplugin") version "0.0.13"
    application
}

group = "com.chat.async"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web", "javafx.swing")
}

dependencies {
    implementation("io.vertx:vertx-core:4.5.3")
    implementation("io.vertx:vertx-web:4.5.3")
    implementation("com.rabbitmq:amqp-client:5.16.0")

    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.11.0")

    implementation("org.openjfx:javafx-controls:21")
    implementation("org.openjfx:javafx-fxml:21")
    implementation("org.openjfx:javafx-graphics:21")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(22)
}

application {
    mainClass.set("Async")
}