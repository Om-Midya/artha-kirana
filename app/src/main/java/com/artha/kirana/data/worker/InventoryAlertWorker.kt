package com.artha.kirana.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.artha.kirana.data.db.dao.ItemsDao
import com.artha.kirana.data.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic low-stock check. Fires a notification per low item and CANCELS notifications for
 * items now back above threshold (auto-clear on restock — notification id == item id).
 */
@HiltWorker
class InventoryAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val itemsDao: ItemsDao,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val low = itemsDao.lowStock()
        val lowIds = low.map { it.id }.toSet()
        // Auto-clear: cancel notifications for items no longer low.
        itemsDao.getAllOnce().forEach { item ->
            if (item.id !in lowIds) notifications.cancel(item.id)
        }
        low.forEach { notifications.notifyLowStock(it) }
        return Result.success()
    }
}
