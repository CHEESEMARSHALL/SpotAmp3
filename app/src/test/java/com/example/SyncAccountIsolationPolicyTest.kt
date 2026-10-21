package com.example

import com.example.data.SyncAccountIsolationPolicy
import com.example.data.SyncAccountIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAccountIsolationPolicyTest {
    private val accountA = SyncAccountIdentity("https://example.test", "device-a")
    private val accountB = SyncAccountIdentity("https://example.test", "device-b")

    @Test
    fun oldAccountCannotCommitAfterAccountSwitch() {
        assertFalse(SyncAccountIsolationPolicy.mayCommit(accountA, accountB))
    }

    @Test
    fun sameOriginAndAccountCanCommit() {
        assertTrue(SyncAccountIsolationPolicy.mayCommit(accountA, accountA))
    }

    @Test
    fun missingCurrentIdentityBlocksCommit() {
        assertFalse(SyncAccountIsolationPolicy.mayCommit(accountA, null))
    }

    @Test
    fun identityNormalizesOriginAndAccountId() {
        val identity = SyncAccountIsolationPolicy.identity(" HTTPS://Example.Test:443/ ", "  device-a  ")

        assertEquals(SyncAccountIdentity("https://example.test", "device-a"), identity)
    }

    @Test
    fun malformedOrBlankAccountConfigurationCannotCommit() {
        assertNull(SyncAccountIsolationPolicy.identity("not a url", "device-a"))
        assertNull(SyncAccountIsolationPolicy.identity("https://example.test", "  "))
    }

    @Test
    fun originSwitchCannotReuseSameAccountId() {
        val old = SyncAccountIsolationPolicy.identity("https://old.example.test", "device-a")!!
        val current = SyncAccountIsolationPolicy.identity("https://new.example.test", "device-a")!!

        assertFalse(SyncAccountIsolationPolicy.mayCommit(old, current))
    }}
