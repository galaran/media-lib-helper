package me.galaran.medialib

import java.nio.file.Files
import java.nio.file.Paths

private val MEDIA_FILE_NAME_REGEX = Regex("""\w{3}_\d{8}_\d{6}\.\w{3,4}""")

private val GALLERY_ROOT = Paths.get("C:/YandexDisk/Photos")

fun main(args: Array<String>) {
    var fileCount = 0
    var dirCount = 0
    val exts = HashMap<String, Int>()
    var illegalName = 0
    var sizeBytes = 0L

    Files.walk(GALLERY_ROOT).forEach { path ->
        val file = path.toFile()
        when {
            file.isFile -> fileCount++
            file.isDirectory -> dirCount++
            else -> throw IllegalStateException("Not a dir, not a file")
        }
        if (file.isFile) {
            val ext = file.name.substringAfterLast('.', "no-ext").toLowerCase()
            exts.merge(ext, 1, { prev, _ -> prev + 1 })

            if (!file.name.matches(MEDIA_FILE_NAME_REGEX)) {
                illegalName++
                println(GALLERY_ROOT.relativize(path))
            }

            sizeBytes += file.length()
        }
    }
    println("\nDirs: $dirCount | Files: $fileCount | Total = ${sizeBytes / 1024 / 1024} MB")
    exts.forEach { ext, count ->  println(".$ext: $count")}
    println("Illegal file name count = $illegalName")
}
