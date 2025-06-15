plugins {
  kotlin("jvm")
  application
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("io.ktor:ktor-server-core:2.3.2")
  implementation("io.ktor:ktor-server-netty:2.3.2")
  implementation("io.ktor:ktor-server-cors:2.3.2")
  implementation("io.ktor:ktor-server-content-negotiation:2.3.2")
  implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
  implementation("org.bytedeco:javacv-platform:1.5.9")
  implementation("org.slf4j:slf4j-simple:2.0.17")
}

application {
  mainClass.set("br.com.colman.timelapser.MainKt")
}

kotlin.jvmToolchain {
  languageVersion.set(JavaLanguageVersion.of(20))
}
