package com.attiqtherehman.kharcha.data.backup

import android.content.Context
import android.net.Uri
import com.attiqtherehman.kharcha.core.common.Ids
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Receipt images live in app-private storage under files/attachments/<uuid>.<ext>. */
@Singleton
class AttachmentStore @Inject constructor(@ApplicationContext private val context: Context) {

    val directory: File get() = File(context.filesDir, "attachments").apply { mkdirs() }

    fun file(name: String): File = File(directory, name)

    fun exists(name: String): Boolean = file(name).isFile

    /** Copies the picked content into private storage and returns the stored file name. */
    fun importFrom(uri: Uri, extension: String = "jpg"): String {
        val name = "${Ids.newId()}.$extension"
        context.contentResolver.openInputStream(uri)?.use { input -> file(name).outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("cannot open $uri")
        return name
    }

    fun write(name: String, input: InputStream, dir: File = directory) {
        File(dir, name).outputStream().use { input.copyTo(it) }
    }

    fun delete(name: String) {
        file(name).delete()
    }

    fun list(): List<String> = directory.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()

    /** Staging directory used during restore; swapped in only after the database commit succeeds. */
    fun stagingDirectory(): File = File(context.filesDir, "attachments.restore").apply { deleteRecursively(); mkdirs() }

    fun swapInStaging(staging: File) {
        val old = File(context.filesDir, "attachments.old")
        old.deleteRecursively()
        val current = directory
        if (current.exists()) current.renameTo(old)
        staging.renameTo(File(context.filesDir, "attachments"))
        old.deleteRecursively()
    }

    fun mergeFromStaging(staging: File) {
        staging.listFiles()?.forEach { f -> val target = file(f.name); if (!target.exists()) f.renameTo(target) }
        staging.deleteRecursively()
    }
}
