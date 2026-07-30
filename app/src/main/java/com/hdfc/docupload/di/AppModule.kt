package com.hdfc.docupload.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.hdfc.docupload.data.local.AppDatabase
import com.hdfc.docupload.data.local.DocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
