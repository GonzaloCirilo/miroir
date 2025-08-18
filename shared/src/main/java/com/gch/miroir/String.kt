package com.gch.miroir

import java.io.File
import java.util.concurrent.TimeUnit

fun String.runCommand(
    workingDir: File = File("."),
    timeoutInSeconds: Long = 60,
    timeUnit: TimeUnit = TimeUnit.SECONDS
): String? {
    return try {
        val parts = this.split("\\s".toRegex())
        val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        proc.waitFor(timeoutInSeconds, timeUnit)
        val output = proc.inputStream.bufferedReader().readText()
        val error = proc.errorStream.bufferedReader().readText()

        if (error.isNotBlank()) {
            System.err.println("Command error: $error")
        }
        output
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}