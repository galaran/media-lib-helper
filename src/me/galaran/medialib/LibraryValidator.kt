package me.galaran.medialib

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField.*
import java.time.temporal.TemporalAccessor
import kotlin.system.exitProcess

val MONTH_DIR_REGEX = Regex("""\d{4}_\d{2}""") // yyyy_MM
val EVENT_DIR_REGEX = Regex("""(\d{4}-\d{2}-\d{2})_(.+)""") // yyyy-MM-dd_<event_name>
val MEDIA_FILE_DATE_TIME_REGEX = Regex("""(\w{3})_(\d{8}_\d{6})(_.+?)?\.(\w{3,4})""") // <type_prefix>_yyyyMMdd_HHmmss[_descr].<ext>
val MEDIA_FILE_DATE_NUM_REGEX = Regex("""(\w{3})_(\d{8})_N\d{1,2}(_.+?)?\.(\w{3,4})""") // <type_prefix>_yyyyMMdd_N1[_descr].<ext>

val MONTH_DIR_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy_MM")
val EVENT_DIR_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val MEDIA_FILE_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
val MEDIA_FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

enum class MediaType(val namePrefix: String, vararg val validExtensions: String) {
    IMAGE("IMG", "jpg", "png"),
    PANORAMA("PAN", "jpg"),
    MAP("MAP", "jpg", "png"),
    SCREENSHOT("SCR", "png"),
    VIDEO("VID", "mp4", "mov"),
    RECORD("REC", "opus")
}

var invalidCount = 0

val validMediaCount = LinkedHashMap<MediaType, Int>(MediaType.values().associate { Pair(it, 0) })

var totalFilesCount = 0
var totalSize = 0L

fun main(args: Array<String>) {
    if (args.size != 1) {
        println("Usage: java me.galaran.medialib.LibraryValidator <media_library_root>")
        exitProcess(0)
    }
    val libraryRoot: Path = Paths.get(args[0])
    if (!libraryRoot.isDirectory()) {
        println("${args[0]}: not a directory")
        exitProcess(1)
    }

    libraryRoot.list { monthDir ->
        if (!monthDir.isDirectory()) {
            invalidStructure(monthDir)
            return@list
        }
        if (!monthDir.lastName.matches(MONTH_DIR_REGEX)) {
            invalidName(monthDir)
            return@list
        }

        val monthDirTemporal: TemporalAccessor
        try {
            monthDirTemporal = MONTH_DIR_DATE.parse(monthDir.lastName)
        } catch (ex: DateTimeParseException) {
            invalidDate(monthDir)
            return@list
        }
        val year = monthDirTemporal[YEAR]
        val month = monthDirTemporal[MONTH_OF_YEAR]

        monthDir.list { monthEntry ->
            if (monthEntry.isDirectory()) {
                handleEventDirectory(monthEntry, year, month)
            } else {
                handleFile(monthEntry, year, month)
            }
        }
    }

    println("\nInvalid count = $invalidCount")

    println("\nValid media files:")
    validMediaCount.forEach { (type, count) -> println("$type: $count")}

    println("\nTotal files: $totalFilesCount | Size = ${"%.1f".format(totalSize / 1024f / 1024f / 1024f)} GB")
}

fun handleEventDirectory(eventDir: Path, expectedYear: Int, expectedMonth: Int) {
    val eventDirMatcher = EVENT_DIR_REGEX.find(eventDir.lastName)

    var eventDate = ANY_DATE
    if (eventDirMatcher == null) {
        invalidName(eventDir)
    } else {
        try {
            EVENT_DIR_DATE.parse(eventDirMatcher.groupValues[1]).let {
                if (it[YEAR] == expectedYear && it[MONTH_OF_YEAR] == expectedMonth) {
                    eventDate = it[DAY_OF_MONTH]
                } else {
                    invalidDate(eventDir)
                }
            }
        } catch (ex: DateTimeParseException) {
            invalidDate(eventDir)
        }
    }

    eventDir.list { eventEntry ->
        if (eventEntry.isDirectory()) { // any name
            eventEntry.list { eventSubEntry ->
                if (eventSubEntry.isDirectory()) {
                    invalidStructure(eventSubEntry)
                } else {
                    handleFile(eventSubEntry, expectedYear, expectedMonth, eventDate)
                }
            }
        } else {
            handleFile(eventEntry, expectedYear, expectedMonth, eventDate)
        }
    }
}

const val ANY_DATE = -1

fun handleFile(file: Path, expectedYear: Int, expectedMonth: Int, expectedDate: Int = ANY_DATE) {
    totalFilesCount++
    totalSize += Files.size(file)

    if (!tryMatchMediaFile(file, MEDIA_FILE_DATE_TIME_REGEX, MEDIA_FILE_DATE_TIME, expectedYear, expectedMonth, expectedDate)
            && !tryMatchMediaFile(file, MEDIA_FILE_DATE_NUM_REGEX, MEDIA_FILE_DATE, expectedYear, expectedMonth, expectedDate)) {
        invalidName(file)
    }
}

fun tryMatchMediaFile(file: Path, pattern: Regex, datePattern: DateTimeFormatter,
                      expectedYear: Int, expectedMonth: Int, expectedDate: Int): Boolean {
    val matchResult = pattern.find(file.lastName) ?: return false

    val fileChrono: TemporalAccessor
    try {
        fileChrono = datePattern.parse(matchResult.groupValues[2])
    } catch (ex: DateTimeParseException) {
        invalidDate(file)
        return true
    }
    if (fileChrono[YEAR] != expectedYear || fileChrono[MONTH_OF_YEAR] != expectedMonth) {
        invalidDate(file)
        return true
    }
    if (expectedDate != ANY_DATE && fileChrono[DAY_OF_MONTH] != expectedDate) {
        invalidDate(file)
        return true
    }

    val mediaClass = MediaType.values().find { it.namePrefix == matchResult.groupValues[1] }
    if (mediaClass == null) {
        invalidName(file)
    } else {
        if (mediaClass.validExtensions.find { it == matchResult.groupValues[4] } == null) {
            invalidName(file)
        } else {
            validMediaCount.computeIfPresent(mediaClass, { _, prev -> prev + 1 })
        }
    }

    return true
}

fun invalidName(path: Path) = invalid(path, "name")
fun invalidDate(path: Path) = invalid(path, "date")
fun invalidStructure(path: Path) = invalid(path, "structure")
fun invalid(path: Path, classs: String) {
    println("[Invalid $classs] ${path.toAbsolutePath()}")
    invalidCount++
}

val Path.lastName: String get() = this.fileName.toString()
fun Path.isDirectory(): Boolean = Files.isDirectory(this)
fun Path.list(listFunc: (Path) -> Unit) = Files.list(this).forEach(listFunc)
