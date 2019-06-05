package me.galaran.medialib

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val LIBRARY_ROOT = Paths.get("C:/YandexDisk/Photos")

// Name patterns
private val MONTH_DIR_REGEX = Regex("""\d{4}_\d{2}""") // yyyy_MM
private val EVENT_DIR_REGEX = Regex("""\d{4}-\d{2}-\d{2}_(.+)""") // yyyy-MM-dd_<event_name>
private val MEDIA_FILE_DATE_TIME_REGEX = Regex("""(\w+?)_\d{8}_\d{6}(_.+?)?\.(\w+)""") // <type_prefix>_yyyyMMdd_hhmmss[_descr].<ext>
private val MEDIA_FILE_DATE_NUM_REGEX = Regex("""(\w+?)_\d{8}_N\d{3}(_.+?)?\.(\w+)""") // <type_prefix>_yyyyMMdd_N001[_descr].<ext>

enum class MediaType(val namePrefix: String, vararg val validExtensions: String) {
    IMAGE("IMG", "jpg", "png"),
    PANORAMA("PAN", "jpg"),
    MAP("MAP", "jpg", "png"),
    SCREENSHOT("SCR", "png"),
    VIDEO("VID", "mp4", "mov"),
    RECORD("REC", "opus")
}

var invalidNameCount = 0

val validMediaCount = LinkedHashMap<MediaType, Int>(MediaType.values().associate { Pair(it, 0) })

var totalFilesCount = 0
var totalSize = 0L

fun main(args: Array<String>) {
    Files.walk(LIBRARY_ROOT, 1).filter { it != LIBRARY_ROOT }.forEach { monthDir ->
        if (!monthDir.fileName.toString().matches(MONTH_DIR_REGEX)) {
            handleInvalidName(monthDir)
        }

        Files.walk(monthDir).filter { it != monthDir }.forEach { path ->
            val file = path.toFile()
            if (file.isDirectory) {
                if (!file.name.matches(EVENT_DIR_REGEX)) {
                    handleInvalidName(path)
                }
            } else {
                totalFilesCount++
                totalSize += file.length()

                if (!tryMatchMediaFile(file, MEDIA_FILE_DATE_TIME_REGEX) && !tryMatchMediaFile(file, MEDIA_FILE_DATE_NUM_REGEX)) {
                    handleInvalidName(path)
                }
            }
        }
    }

    println("\nInvalid name count = $invalidNameCount")

    println("\nValid media files:")
    validMediaCount.forEach { (type, count) -> println("$type: $count")}

    println("\nTotal files: $totalFilesCount | Size = ${totalSize / 1024 / 1024} MB")
}

fun tryMatchMediaFile(file: File, pattern: Regex): Boolean {
    val matchResult = pattern.find(file.name) ?: return false

    val mediaClass = MediaType.values().find { it.namePrefix == matchResult.groupValues[1] }
    if (mediaClass == null) {
        handleInvalidName(file.toPath())
    } else {
        if (mediaClass.validExtensions.find { it == matchResult.groupValues[3] } == null) {
            handleInvalidName(file.toPath())
        } else {
            validMediaCount.computeIfPresent(mediaClass, { _, prev -> prev + 1 })
        }
    }

    return true
}

fun handleInvalidName(path: Path) {
    println("[Invalid name] ${path.toAbsolutePath()}")
    invalidNameCount++
}
