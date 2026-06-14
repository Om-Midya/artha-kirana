package com.artha.kirana.di

import com.artha.kirana.data.remote.ChatClient
import com.artha.kirana.data.remote.FallbackChatClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmBindModule {
    @Binds
    @Singleton
    abstract fun bindChatClient(impl: FallbackChatClient): ChatClient
}
