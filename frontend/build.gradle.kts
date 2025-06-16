plugins {
  kotlin("multiplatform")
  id("org.jetbrains.compose") version "1.5.10"
}

kotlin {
  js(IR) {
    browser {
      binaries.executable()
    }
  }
  sourceSets {
    val jsMain by getting {
      dependencies {
        implementation(compose.html.core)
        implementation(compose.runtime)
        implementation("io.ktor:ktor-client-core:2.3.2")
        implementation("io.ktor:ktor-client-js:2.3.2")
        implementation("io.ktor:ktor-client-content-negotiation:2.3.2")
        implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.2")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
      }
    }
  }
}