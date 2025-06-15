package br.com.colman.timelapser

import java.io.File

object VideoCompiler {
    fun compileFramesToVideo(framesDir: File, outputFile: String) {
        val cmd = listOf(
            "ffmpeg",
            "-framerate", "10",
            "-pattern_type", "glob",
            "-i", "${framesDir.absolutePath}/frame_*.jpg",
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            outputFile
        )
        println("Running: ${cmd.joinToString(" ")}")
        ProcessBuilder(cmd)
            .inheritIO()
            .start()
            .waitFor()
    }
}