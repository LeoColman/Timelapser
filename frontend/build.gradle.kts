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
        implementation(compose.web.core)
        implementation(kotlin("stdlib-js"))
      }
    }
  }
}