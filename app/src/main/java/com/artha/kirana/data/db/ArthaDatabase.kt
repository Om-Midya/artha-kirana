package com.artha.kirana.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.artha.kirana.data.db.dao.ItemsDao
import com.artha.kirana.data.db.dao.KhataDao
import com.artha.kirana.data.db.dao.KhataTransactionDao
import com.artha.kirana.data.db.dao.PurchasesDao
import com.artha.kirana.data.db.dao.SalesDao
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.data.db.entity.PurchaseEntity
import com.artha.kirana.data.db.entity.SaleEntity

@Database(
    entities = [
        ItemEntity::class,
        SaleEntity::class,
        PurchaseEntity::class,
        KhataEntity::class,
        KhataTransactionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ArthaDatabase : RoomDatabase() {
    abstract fun itemsDao(): ItemsDao
    abstract fun salesDao(): SalesDao
    abstract fun purchasesDao(): PurchasesDao
    abstract fun khataDao(): KhataDao
    abstract fun khataTransactionDao(): KhataTransactionDao
}
