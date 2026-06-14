package com.artha.kirana.di

import android.content.Context
import androidx.room.Room
import com.artha.kirana.data.db.ArthaDatabase
import com.artha.kirana.data.db.dao.CustomersDao
import com.artha.kirana.data.db.dao.ItemsDao
import com.artha.kirana.data.db.dao.KhataDao
import com.artha.kirana.data.db.dao.KhataTransactionDao
import com.artha.kirana.data.db.dao.PurchasesDao
import com.artha.kirana.data.db.dao.SalesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ArthaDatabase =
        Room.databaseBuilder(context, ArthaDatabase::class.java, "artha.db")
            // SQLCipher swap-in seam (deferred, design §3.1):
            // .openHelperFactory(net.sqlcipher.database.SupportFactory(passphrase))
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideItemsDao(db: ArthaDatabase): ItemsDao = db.itemsDao()

    @Provides
    fun provideSalesDao(db: ArthaDatabase): SalesDao = db.salesDao()

    @Provides
    fun providePurchasesDao(db: ArthaDatabase): PurchasesDao = db.purchasesDao()

    @Provides
    fun provideKhataDao(db: ArthaDatabase): KhataDao = db.khataDao()

    @Provides
    fun provideKhataTransactionDao(db: ArthaDatabase): KhataTransactionDao = db.khataTransactionDao()

    @Provides
    fun provideCustomersDao(db: ArthaDatabase): CustomersDao = db.customersDao()
}
