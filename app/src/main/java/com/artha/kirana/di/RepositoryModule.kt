package com.artha.kirana.di

import com.artha.kirana.data.repository.CustomerRepositoryImpl
import com.artha.kirana.data.repository.InventoryRepositoryImpl
import com.artha.kirana.data.repository.KhataRepositoryImpl
import com.artha.kirana.data.repository.SalesRepositoryImpl
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSalesRepository(impl: SalesRepositoryImpl): SalesRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindKhataRepository(impl: KhataRepositoryImpl): KhataRepository

    @Binds
    @Singleton
    abstract fun bindCustomerRepository(impl: CustomerRepositoryImpl): CustomerRepository
}
