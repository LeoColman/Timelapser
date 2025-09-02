plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "1.9.10"
  application
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
  implementation("org.bytedeco:javacv-platform:1.5.9")
  implementation("org.slf4j:slf4j-simple:2.0.17")
  implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation("io.insert-koin:koin-core:3.5.6")
  implementation("io.insert-koin:koin-logger-slf4j:3.5.6")

  testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
  testImplementation("io.kotest:kotest-assertions-core:5.9.1")
  testImplementation("io.mockk:mockk:1.13.12")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
  testImplementation("org.testcontainers:testcontainers:1.19.7")
  testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
}

application {
  mainClass.set("br.com.colman.timelapser.MainKt")
}

kotlin.jvmToolchain {
  languageVersion.set(JavaLanguageVersion.of(20))
}

tasks.test {
  useJUnitPlatform()
}
