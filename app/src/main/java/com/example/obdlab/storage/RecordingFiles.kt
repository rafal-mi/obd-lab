package com.example.obdlab.storage

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Private durable files, independent of the current in-memory recording. */
class RecordingFiles(root: File) {
    private val directory = File(root, "recordings")
    data class Saved(val csv: File, val log: File)

    fun save(startedAt: Long, csv: String, log: String): Saved {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create recordings directory" }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS_Z", Locale.ROOT).format(Date(startedAt))
        val name = "accelerator_${stamp}_${UUID.randomUUID()}"
        val csvFile = File(directory, "$name.csv")
        val logFile = File(directory, "$name.txt")
        try {
            writeNew(csvFile, csv)
            writeNew(logFile, log)
            return Saved(csvFile, logFile)
        } catch (e: Exception) {
            csvFile.delete()
            logFile.delete()
            throw e
        }
    }

    private fun writeNew(file: File, value: String) {
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            temp.outputStream().use { stream ->
                stream.write(value.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            check(!file.exists() && temp.renameTo(file)) { "Cannot publish ${file.name}" }
        } finally { temp.delete() }
    }

    fun list(): List<Saved> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "csv" }
        .map { Saved(it, File(directory, it.nameWithoutExtension + ".txt")) }
        .filter { it.log.isFile }
        .sortedByDescending { it.csv.lastModified() }
}
