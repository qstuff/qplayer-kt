package org.qstuff.qplayer.ui.filebrowser

import android.content.Context
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import java.io.File

/** A mounted storage volume the file browser can browse (internal storage, SD card, …). */
data class StorageRoot(
    val dir: File,
    val label: String,
    val isRemovable: Boolean
)

object StorageRoots {

    private const val APP_DIR_MARKER = "/Android/data/"

    /**
     * The roots of all currently mounted storage volumes.
     *
     * Android hands each app a private directory on every mounted volume
     * (e.g. `/storage/3437-6533/Android/data/<package>/files`); cutting that off at `/Android/data/`
     * gives the volume root — no hard-coded, device-specific SD card ids. Unmounted volumes come
     * back as null and are skipped. Labels ("SD card", …) come from the system's StorageVolume.
     */
    fun find(context: Context): List<StorageRoot> {
        val storageManager = context.getSystemService(StorageManager::class.java)
        return ContextCompat.getExternalFilesDirs(context, null)
            .filterNotNull()
            .mapNotNull { appDir ->
                val path = appDir.absolutePath
                val markerIndex = path.indexOf(APP_DIR_MARKER)
                if (markerIndex <= 0) return@mapNotNull null
                val root = File(path.substring(0, markerIndex))
                val volume = storageManager?.getStorageVolume(root)
                StorageRoot(
                    dir = root,
                    label = volume?.getDescription(context) ?: root.name,
                    isRemovable = volume?.isRemovable ?: false
                )
            }
            .distinctBy { it.dir.absolutePath }
    }
}
