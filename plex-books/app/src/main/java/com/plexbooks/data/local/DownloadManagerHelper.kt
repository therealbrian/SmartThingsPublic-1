package com.plexbooks.data.local

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(ratingKey: String, title: String, url: String): Long {
        val fileName = "plexbooks_${ratingKey}.mp3"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        return dm.enqueue(request)
    }

    fun queryStatus(downloadId: Long): Int? {
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
        return if (cursor.moveToFirst()) {
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                .also { cursor.close() }
        } else {
            cursor.close()
            null
        }
    }

    fun localPath(ratingKey: String): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        return "${dir?.absolutePath}/plexbooks_${ratingKey}.mp3"
    }

    fun fileExists(ratingKey: String): Boolean =
        java.io.File(localPath(ratingKey)).exists()

    fun delete(downloadId: Long) = dm.remove(downloadId)
}
