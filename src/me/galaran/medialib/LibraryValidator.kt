package me.galaran.medialib

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField.*
import java.time.temporal.TemporalAccessor
import kotlin.system.exitProcess

val YEAR_DIR_REGEX = Regex("""\d{4}""") // yyyy
val MONTH_DIR_REGEX = Regex("""\d{4}_\d{2}""") // yyyy_MM
val EVENT_DIR_REGEX = Regex("""(\d{4}-\d{2}-\d{2})_(.+)""") // yyyy-MM-dd_<event_name>
val MEDIA_FILE_DATE_TIME_REGEX = Regex("""(\w{3})_(\d{8}_\d{6})(_.+?)?\.(\w{3,4})""") // <type_prefix>_yyyyMMdd_HHmmss[_descr].<ext>
val MEDIA_FILE_DATE_NUM_REGEX = Regex("""(\w{3})_(\d{8})_N\d{1,2}(_.+?)?\.(\w{3,4})""") // <type_prefix>_yyyyMMdd_N1[_descr].<ext>

val YEAR_DIR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy")
val MONTH_DIR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy_MM")
val EVENT_DIR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val MEDIA_FILE_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
val MEDIA_FILE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

enum class MediaFileType(val namePrefix: String, vararg val validExtensions: String) {
    IMAGE("IMG", "jpg", "png"),
    PANORAMA("PAN", "jpg"),
    MAP("MAP", "jpg", "png"),
    SCREENSHOT("SCR", "png"),
    VIDEO("VID", "mp4", "mov"),
    RECORD("REC", "opus")
}

var invalidObjectCount = 0

val validMediaFileCount = LinkedHashMap<MediaFileType, Int>(MediaFileType.values().associateWith { 0 })

var totalFileCount = 0
var totalSizeBytes = 0L

fun main(args: Array<String>) {
    if (args.size != 1) {
        println("Usage: java -jar ... <media_library_root>")
        exitProcess(0)
    }

    val libraryRoot: Path = Paths.get(args[0])
    if (!libraryRoot.isDirectory()) {
        println("${args[0]}: media library root is not a directory")
        exitProcess(1)
    }

    for (yearDir in Files.list(libraryRoot)) {
        val yearDirTemporal: TemporalAccessor = validateTemporalDirectory(yearDir,
            YEAR_DIR_REGEX, YEAR_DIR_DATE_FORMAT) ?: continue
        val year: Int = yearDirTemporal[YEAR]

        for (monthDir in Files.list(yearDir)) {
            val monthDirTemporal: TemporalAccessor = validateTemporalDirectory(monthDir,
                MONTH_DIR_REGEX, MONTH_DIR_DATE_FORMAT) ?: continue
            if (monthDirTemporal[YEAR] != year) {
                invalidDate(monthDir)
                continue
            }
            val month: Int = monthDirTemporal[MONTH_OF_YEAR]

            for (monthEntry in Files.list(monthDir)) {
                if (monthEntry.isDirectory()) {
                    validateEventDirectory(monthEntry, year, month)
                } else {
                    validateFile(monthEntry, year, month)
                }
            }
        }
    }

    println("\nInvalid count = $invalidObjectCount")

    println("\nValid media files:")
    validMediaFileCount.forEach { (type, count) -> println("$type: $count")}

    println("\nTotal files: $totalFileCount | Size = ${"%.1f".format(totalSizeBytes / 1024f / 1024f / 1024f)} GB")
}

fun validateTemporalDirectory(temporalDir: Path, nameRegex: Regex, nameFormat: DateTimeFormatter): TemporalAccessor? {
    if (!temporalDir.isDirectory()) {
        invalidStructure(temporalDir)
        return null
    }
    if (!temporalDir.fileName.toString().matches(nameRegex)) {
        invalidName(temporalDir)
        return null
    }

    return try {
        nameFormat.parse(temporalDir.fileName.toString())
    } catch (ex: DateTimeParseException) {
        invalidDate(temporalDir)
        null
    }
}

fun validateEventDirectory(eventDir: Path, expectedYear: Int, expectedMonth: Int) {
    val eventDirMatcher = EVENT_DIR_REGEX.find(eventDir.fileName.toString())
    if (eventDirMatcher == null) {
        invalidName(eventDir)
        return
    }

    val eventDate: Int
    try {
        EVENT_DIR_DATE_FORMAT.parse(eventDirMatcher.groupValues[1]).let {
            if (it[YEAR] == expectedYear && it[MONTH_OF_YEAR] == expectedMonth) {
                eventDate = it[DAY_OF_MONTH]
            } else {
                invalidDate(eventDir)
                return
            }
        }
    } catch (ex: DateTimeParseException) {
        invalidDate(eventDir)
        return
    }

    for (eventEntry in Files.list(eventDir)) {
        if (eventEntry.isDirectory()) { // any name
            for (eventSubEntry in Files.list(eventEntry)) {
                if (eventSubEntry.isDirectory()) {
                    invalidStructure(eventSubEntry)
                } else {
                    validateFile(eventSubEntry, expectedYear, expectedMonth, eventDate)
                }
            }
        } else {
            validateFile(eventEntry, expectedYear, expectedMonth, eventDate)
        }
    }
}

const val ANY_DATE = -1

fun validateFile(file: Path, expectedYear: Int, expectedMonth: Int, expectedDate: Int = ANY_DATE) {
    totalFileCount++
    totalSizeBytes += Files.size(file)

    if (!tryMatchMediaFile(file, MEDIA_FILE_DATE_TIME_REGEX, MEDIA_FILE_DATE_TIME_FORMAT, expectedYear, expectedMonth, expectedDate)
            && !tryMatchMediaFile(file, MEDIA_FILE_DATE_NUM_REGEX, MEDIA_FILE_DATE_FORMAT, expectedYear, expectedMonth, expectedDate)) {
        invalidName(file)
    }
}

fun tryMatchMediaFile(file: Path, pattern: Regex, datePattern: DateTimeFormatter,
                      expectedYear: Int, expectedMonth: Int, expectedDate: Int): Boolean {
    val matchResult = pattern.find(file.fileName.toString()) ?: return false

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

    val mediaType = MediaFileType.values().find { it.namePrefix == matchResult.groupValues[1] }
    if (mediaType == null) {
        invalidName(file)
    } else {
        if (mediaType.validExtensions.find { it == matchResult.groupValues[4] } == null) {
            invalidName(file)
        } else {
            validMediaFileCount.computeIfPresent(mediaType) { _, prev -> prev + 1 }
        }
    }

    return true
}

fun invalidName(path: Path) = invalidX(path, "name")
fun invalidDate(path: Path) = invalidX(path, "date")
fun invalidStructure(path: Path) = invalidX(path, "structure")
fun invalidX(path: Path, x: String) {
    println("[Invalid $x] ${path.toAbsolutePath()}")
    invalidObjectCount++
}

fun Path.isDirectory(): Boolean = Files.isDirectory(this)
