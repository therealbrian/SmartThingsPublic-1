package com.plexbooks.data.local

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadCompletionReceiver : BroadcastReceiver() {

    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var downloadHelper: DownloadManagerHelper

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newStatus = when (downloadHelper.queryStatus(downloadId)) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.DONE
                    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                    else -> null
                }
                if (newStatus != null) {
                    downloadDao.updateStatusByDownloadId(downloadId, newStatus)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
