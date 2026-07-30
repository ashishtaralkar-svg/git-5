package com.hdfc.docupload.di

import com.hdfc.docupload.data.repository.ApplicationRepositoryImpl
import com.hdfc.docupload.data.repository.AuthRepositoryImpl
import com.hdfc.docupload.data.repository.DocumentRepositoryImpl
import com.hdfc.docupload.domain.repository.ApplicationRepository
import com.hdfc.docupload.domain.repository.AuthRepository
import com.hdfc.docupload.domain.repository.DocumentRepository
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
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindApplicationRepository(impl: ApplicationRepositoryImpl): ApplicationRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository
}
