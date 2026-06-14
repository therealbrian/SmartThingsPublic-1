package com.plexbooks.di

import android.content.Context
import androidx.room.Room
import com.plexbooks.data.api.PlexAuthApi
import com.plexbooks.data.api.PlexMediaApi
import com.plexbooks.data.local.AppDatabase
import com.plexbooks.data.local.ProgressDao
import com.plexbooks.data.prefs.PlexPreferences
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

private const val PLEX_TV_BASE = "https://plex.tv/api/v2/"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun providePlexHeaderInterceptor(prefs: PlexPreferences): Interceptor = Interceptor { chain ->
        val clientId = runBlocking { prefs.ensureClientId() }
        val token = runBlocking { prefs.authToken.first() }
        val req = chain.request().newBuilder()
            .addHeader("X-Plex-Client-Identifier", clientId)
            .addHeader("X-Plex-Product", "PlexBooks")
            .addHeader("X-Plex-Version", "1.0.0")
            .addHeader("X-Plex-Platform", "Android")
            .addHeader("Accept", "application/json")
            .apply { if (!token.isNullOrBlank()) addHeader("X-Plex-Token", token) }
            .build()
        chain.proceed(req)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(plexHeaderInterceptor: Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(plexHeaderInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLEX_TV_BASE)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun providePlexAuthApi(@Named("auth") retrofit: Retrofit): PlexAuthApi =
        retrofit.create(PlexAuthApi::class.java)

    @Provides
    @Singleton
    @Named("media")
    fun provideMediaRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        prefs: PlexPreferences
    ): Retrofit {
        val serverUri = runBlocking { prefs.serverUri.first() } ?: "http://localhost:32400/"
        val base = if (serverUri.endsWith("/")) serverUri else "$serverUri/"
        return Retrofit.Builder()
            .baseUrl(base)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun providePlexMediaApi(
        @Named("media") retrofit: Retrofit,
        prefs: PlexPreferences
    ): PlexMediaApi = DynamicPlexMediaApi(retrofit, prefs)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "plexbooks.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProgressDao(db: AppDatabase): ProgressDao = db.progressDao()

    @Provides
    fun provideDownloadDao(db: AppDatabase): com.plexbooks.data.local.DownloadDao = db.downloadDao()
}

/** Wraps PlexMediaApi so it always picks up the current server URI at call time. */
class DynamicPlexMediaApi(
    private val baseRetrofit: Retrofit,
    private val prefs: PlexPreferences
) : PlexMediaApi {

    private fun api(): PlexMediaApi {
        val serverUri = runBlocking { prefs.serverUri.first() } ?: return baseRetrofit.create(PlexMediaApi::class.java)
        val base = if (serverUri.endsWith("/")) serverUri else "$serverUri/"
        return baseRetrofit.newBuilder().baseUrl(base).build().create(PlexMediaApi::class.java)
    }

    override suspend fun getLibrarySections() = api().getLibrarySections()
    override suspend fun getSectionItems(sectionId: String, type: Int?, sort: String, start: Int, size: Int, title: String?) =
        api().getSectionItems(sectionId, type, sort, start, size, title)
    override suspend fun getChildren(ratingKey: String, includeChapters: Int) = api().getChildren(ratingKey, includeChapters)
    override suspend fun getChapters(ratingKey: String) = api().getChapters(ratingKey)
    override suspend fun getRecentlyAdded(start: Int, size: Int) = api().getRecentlyAdded(start, size)
    override suspend fun getOnDeck(start: Int, size: Int) = api().getOnDeck(start, size)
    override suspend fun getMetadata(ratingKey: String, includeChapters: Int) = api().getMetadata(ratingKey, includeChapters)
    override suspend fun reportProgress(ratingKey: String, identifier: String, timeMs: Long, state: String, durationMs: Long) =
        api().reportProgress(ratingKey, identifier, timeMs, state, durationMs)
    override suspend fun markPlayed(ratingKey: String, identifier: String) = api().markPlayed(ratingKey, identifier)
    override suspend fun markUnplayed(ratingKey: String, identifier: String) = api().markUnplayed(ratingKey, identifier)
}
