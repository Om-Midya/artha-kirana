package com.artha.kirana.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.artha.kirana.data.db.entity.ItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts/cancels low-stock notifications. Notification id == item id (stable → auto-clearable). */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "INVENTORY_ALERTS"
    }

    init {
        val mgr = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Inventory alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Low-stock reminders" }
        mgr.createNotificationChannel(channel)
    }

    fun notifyLowStock(item: ItemEntity) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Low stock: ${item.name}")
            .setContentText("Only ${item.qtyInStock} ${item.unit} left — reorder soon.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(item.id.toInt(), notification)
    }

    fun cancel(itemId: Long) {
        NotificationManagerCompat.from(context).cancel(itemId.toInt())
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
