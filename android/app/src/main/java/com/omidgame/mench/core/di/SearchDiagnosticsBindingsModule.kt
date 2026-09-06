package com.omidgame.mench.core.di

import com.omidgame.mench.feature.diagnostics.data.DiagnosticsRepositoryImpl
import com.omidgame.mench.feature.diagnostics.domain.DiagnosticsRepository
import com.omidgame.mench.feature.search.data.SearchRepositoryImpl
import com.omidgame.mench.feature.search.domain.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchDiagnosticsBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository
}
