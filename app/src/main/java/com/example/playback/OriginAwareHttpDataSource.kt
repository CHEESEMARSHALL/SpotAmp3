package com.example.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.data.originCredentialsForUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Adds credentials at the final media request boundary. A single global header map
 * would leak the SpotCore bearer token to Plex (or an unrelated stream URL).
 */
@UnstableApi
class OriginAwareHttpDataSourceFactory(
    private val plexBaseUrl: () -> String,
    private val plexToken: () -> String,
    private val companionBaseUrl: () -> String,
    private val companionToken: () -> String
) : DataSource.Factory {
    private val upstreamClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(com.example.data.QaNetworkInterceptor())
        // A network interceptor runs again for every redirect hop, so a cross-origin
        // Location cannot inherit either credential.
        .addNetworkInterceptor { chain ->
            val original = chain.request()
            val credentials = originCredentialsForUrl(
                original.url,
                companionBaseUrl(),
                companionToken(),
                plexBaseUrl(),
                plexToken()
            )
            val cleanHeaders = original.headers.newBuilder()
                .removeAll("Authorization")
                .removeAll("X-Plex-Token")
            credentials.headers.forEach { (name, value) -> cleanHeaders.set(name, value) }
            chain.proceed(original.newBuilder().headers(cleanHeaders.build()).build())
        }
        .build()
    private val upstreamFactory = OkHttpDataSource.Factory(upstreamClient)

    override fun createDataSource(): DataSource {
        val resolver = object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val headers = originCredentialsForUrl(
                    dataSpec.uri.toString(),
                    companionBaseUrl(),
                    companionToken(),
                    plexBaseUrl(),
                    plexToken()
                ).headers
                // Replace, rather than merge, to strip any stale or caller-supplied
                // credentials before following the media request.
                return dataSpec.withRequestHeaders(headers)
            }
        }
        return ResolvingDataSource.Factory(upstreamFactory, resolver).createDataSource()
    }
}
