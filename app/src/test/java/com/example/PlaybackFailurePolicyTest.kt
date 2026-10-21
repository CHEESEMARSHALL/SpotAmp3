package com.example

import androidx.media3.common.PlaybackException
import com.example.playback.PlaybackFailureKind
import com.example.playback.PlaybackFailurePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFailurePolicyTest {
    @Test
    fun missingLocalFileUsesDownloadRecoveryInsteadOfRetry() {
        assertEquals(
            PlaybackFailureKind.LOCAL_FILE_MISSING,
            PlaybackFailurePolicy.classify(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        )
    }

    @Test
    fun decodingAndMalformedAudioUseNonRetryableFileGuidance() {
        listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        ).forEach { errorCode ->
            assertEquals(PlaybackFailureKind.AUDIO_UNPLAYABLE, PlaybackFailurePolicy.classify(errorCode))
        }
    }

    @Test
    fun otherFailuresKeepTheRetryPath() {
        assertEquals(PlaybackFailureKind.RETRYABLE, PlaybackFailurePolicy.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(PlaybackFailureKind.RETRYABLE, PlaybackFailurePolicy.classify(Int.MIN_VALUE))
    }
}
