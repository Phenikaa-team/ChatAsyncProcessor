plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "com.chat.async"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.vertx:vertx-core:4.5.3")
    implementation("io.vertx:vertx-web:4.5.3")
    implementation("com.rabbitmq:amqp-client:5.16.0")
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