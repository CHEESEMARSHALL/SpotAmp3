package com.example.data

import android.content.Context
import androidx.annotation.StringRes
import com.example.R

enum class LastFmAuthorizationStep(@StringRes val failureMessage: Int) {
    START(R.string.settings_lastfm_start_failed),
    COMPLETE(R.string.settings_lastfm_complete_failed)
}

object LastFmAuthorizationFailurePresentation {
    fun message(context: Context, step: LastFmAuthorizationStep): String =
        context.getString(step.failureMessage)
}
