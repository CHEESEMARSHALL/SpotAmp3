package com.example.data

import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Keeps the isolated QA variant on explicitly local/loopback test services. */
object QaNetworkPolicy {
    private val explicitlyAllowedCompanionOrigins = ConcurrentHashMap.newKeySet<String>()

    /**
     * A QR scan is an explicit user decision to connect to this exact
     * companion origin. QA builds may temporarily allow that origin so manual
     * testing can use a controlled HTTPS tunnel; all other external origins
     * remain blocked.
     */
    fun allowExplicitCompanionOrigin(url: HttpUrl) {
        explicitlyAllowedCompanionOrigins += originKey(url)
    }

    fun isAllowed(url: HttpUrl, qaBuild: Boolean = BuildConfig.QA_BUILD): Boolean {
        if (!qaBuild) return true
        val host = url.host.lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "10.0.2.2" ||
            explicitlyAllowedCompanionOrigins.contains(originKey(url))
    }

    private fun originKey(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}"

    internal fun clearExplicitOriginsForTesting() {
        explicitlyAllowedCompanionOrigins.clear()
    }
}

class QaNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!QaNetworkPolicy.isAllowed(request.url)) {
            throw IOException("QA network policy rejected non-local origin: ${request.url.host}")
        }
        return chain.proceed(request)
    }
}
