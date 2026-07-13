package ch.threema.common

import ch.threema.common.files.FileHandle
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.time.Instant
import java.util.UUID

/**
 * Deletes all files and directories inside a directory without deleting the directory itself.
 */
fun File.clearDirectoryRecursively() {
    listFiles()
        ?.forEach { file ->
            file.deleteRecursively()
        }
}

/**
 * Deletes all files inside a directory but leaves directories and their contents, and does not delete the directory itself.
 */
fun File.clearDirectoryNonRecursively() {
    listFiles()
        ?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
}

/**
 * Deletes a file in a more secure manner by moving it to [targetDirectory], thereby marking it for deletion,
 * and then overwriting it with zeroes and  deleting it. Note that this operation is NOT atomic!
 *
 * @param targetDirectory Before deletion, the file will be atomically moved into this directory and renamed.
 * It must be ensured that files in this directory are deleted eventually.
 * @throws IOException If the file could not be deleted, e.g. because it is a directory
 */
@Throws(IOException::class)
fun File.deleteSecurely(targetDirectory: File) {
    if (!exists()) {
        return
    }
    if (isDirectory) {
        throw IOException("Cannot securely delete a directory")
    }

    if (!targetDirectory.exists()) {
        targetDirectory.mkdirs()
    }

    // Rename the file first to achieve pseudo-atomicity,
    // i.e., the file will appear deleted to the caller even if the overwriting with zeroes fails.
    val movedFile = File(targetDirectory, "___DELETE_${UUID.randomUUID()}")
    renameOrThrow(movedFile)

    try {
        movedFile.fillWithZeroes()
    } catch (_: IOException) {
        // ignore, as the file will be deleted afterward anyway
    }
    movedFile.deleteOrThrow()
}

@Throws(IOException::class)
private fun File.fillWithZeroes() {
    val length = length()
    if (length <= 0) {
        return
    }
    RandomAccessFile(this, "rw").use { randomAccessFile ->
        randomAccessFile.seek(0)
        val zeroesBuffer = ByteArray(2048)
        var position: Long = 0
        while (position < length) {
            val writeLength = zeroesBuffer.size.toLong().coerceAtMost(length - position).toInt()
            randomAccessFile.write(zeroesBuffer, 0, writeLength)
            position += writeLength.toLong()
        }
    }
}

@Throws(IOException::class)
fun File.deleteOrThrow() {
    if (exists() && !delete()) {
        throw IOException("Failed to delete file '$name'")
    }
}

@Throws(IOException::class)
fun File.renameOrThrow(dest: File) {
    if (!renameTo(dest)) {
        throw IOException("Failed to rename file '$name' to '${dest.name}'")
    }
}

/**
 * @return The combined size of all files in the directory, including subdirectories, or 0 if the directory does not exist
 * @throws IllegalArgumentException if the file is not a directory
 */
fun File.getTotalDirectorySize(): ByteSize {
    require(!isFile) { "can only be used on directories" }
    return walkTopDown().sumOf { file ->
        if (file.isFile) file.length() else 0
    }
        .bytes
}

fun File.getUniqueFile(fileName: String): File {
    var file = File(this, fileName)
    val extension = file.extensionIncludingDot
    val filePart = file.nameWithoutExtension

    var i = 0
    while (file.exists()) {
        i++
        file = File(this, "$filePart ($i)$extension")
    }
    return file
}

val File.extensionIncludingDot: String
    get() = extension.let { extension ->
        if (extension.isNotEmpty()) ".$extension" else ""
    }

@Throws(IOException::class)
fun File.copyTo(destination: FileHandle) {
    inputStream().copyTo(destination)
}

@Throws(IOException::class)
fun InputStream.copyTo(destination: FileHandle) {
    use { input ->
        destination.write().use { output ->
            input.copyTo(output)
        }
    }
}

fun File.lastModifiedTime(): Instant =
    Instant.ofEpochMilli(lastModified())

val File.isEmptyDirectory: Boolean
    get() = isDirectory && listFiles().isNullOrEmpty()

val File.byteSize: ByteSize
    get() = length().bytes

val Iterable<File>.byteSize: ByteSize
    get() = sumOf { it.length() }.bytes
