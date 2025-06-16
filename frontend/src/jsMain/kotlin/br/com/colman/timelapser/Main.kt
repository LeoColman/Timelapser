package br.com.colman.timelapser

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgb
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable


fun main() {
  renderComposable(rootElementId = "root") {
    var status by remember { mutableStateOf("Idle") }
    LaunchedEffect(Unit) {
      while(true) {
        status = status()
        delay(1_000)
      }
    }
    Style(AppStylesheet)

    H1 { Text("RTSP Timelapse") }

    Div({
      classes(AppStylesheet.buttonBar)
    }) {
      Button(attrs = {
        classes(AppStylesheet.button)
        onClick { start() }
      }) { Text("Start Timelapse") }

      Button(attrs = {
        classes(AppStylesheet.button)
        onClick { stop() }
      }) { Text("Stop & Compile") }
    }

    P {
      B { Text("Status: ") }
      Text(status)
    }
  }
}

object AppStylesheet : StyleSheet() {
  val buttonBar by style {
    display(DisplayStyle.Flex)
    gap(1.em)
    marginBottom(1.em)
  }
  val button by style {
    fontSize(1.em)
    padding(0.6.em, 1.2.em)
    borderRadius(0.5.em)
    cursor("pointer")
    backgroundColor(rgb(62, 102, 255))
    color(Color.white)
    border(0.px)
  }
}