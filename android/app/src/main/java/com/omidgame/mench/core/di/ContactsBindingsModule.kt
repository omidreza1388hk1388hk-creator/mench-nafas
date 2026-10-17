package com.omidgame.mench.core.di

import com.omidgame.mench.feature.contacts.data.ContactsRepositoryImpl
import com.omidgame.mench.feature.contacts.domain.ContactsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContactsBindingsModule {

    @Binds
    @Singleton
    abstract fun bindContactsRepository(impl: ContactsRepositoryImpl): ContactsRepository
}
