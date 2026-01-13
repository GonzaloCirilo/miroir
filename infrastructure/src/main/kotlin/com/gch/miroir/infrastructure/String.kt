package com.gch.miroir.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

suspend fun String.runCommand(
    workingDir: File = File("."),
    timeoutInSeconds: Long = 60,
    timeUnit: TimeUnit = TimeUnit.SECONDS
): String? {
    return withContext(Dispatchers.IO) {
        try {
            val parts = this@runCommand.split("\\s".toRegex())
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
}