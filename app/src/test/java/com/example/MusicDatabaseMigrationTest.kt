package com.example

import com.example.data.MusicDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicDatabaseMigrationTest {
    @Test
    fun currentSchemaUsesExplicitSixToSevenMigration() {
        assertEquals(6, MusicDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, MusicDatabase.MIGRATION_6_7.endVersion)
        assertTrue(MusicDatabase.MIGRATION_6_7.javaClass.name.isNotEmpty())
    }
    @Test
    fun schemaSevenUsesExplicitNoOpMigrationToPreserveData() {
        assertEquals(7, MusicDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, MusicDatabase.MIGRATION_7_8.endVersion)
    }

    @Test
    fun companionPlayOutboxUsesExplicitEightToNineMigration() {
        assertEquals(8, MusicDatabase.MIGRATION_8_9.startVersion)
        assertEquals(9, MusicDatabase.MIGRATION_8_9.endVersion)
        assertTrue(MusicDatabase.MIGRATION_8_9.javaClass.name.isNotEmpty())
    }

    @Test
    fun cachedTracksUseExplicitNineToTenAlbumArtistMigration() {
        assertEquals(9, MusicDatabase.MIGRATION_9_10.startVersion)
        assertEquals(10, MusicDatabase.MIGRATION_9_10.endVersion)
        assertTrue(MusicDatabase.MIGRATION_9_10.javaClass.name.isNotEmpty())
    }

    @Test
    fun recentlyPlayedUsesExplicitTenToElevenAlbumArtistMigration() {
        assertEquals(10, MusicDatabase.MIGRATION_10_11.startVersion)
        assertEquals(11, MusicDatabase.MIGRATION_10_11.endVersion)
        assertTrue(MusicDatabase.MIGRATION_10_11.javaClass.name.isNotEmpty())
    }

    @Test
    fun cachedTracksUseExplicitElevenToTwelveTrackIndexMigration() {
        assertEquals(11, MusicDatabase.MIGRATION_11_12.startVersion)
        assertEquals(12, MusicDatabase.MIGRATION_11_12.endVersion)
    }

    @Test
    fun cachedTracksUseExplicitTwelveToThirteenDiscNumberMigration() {
        assertEquals(12, MusicDatabase.MIGRATION_12_13.startVersion)
        assertEquals(13, MusicDatabase.MIGRATION_12_13.endVersion)
    }

    @Test
    fun playlistsUseExplicitThirteenToFourteenSharingMigration() {
        assertEquals(13, MusicDatabase.MIGRATION_13_14.startVersion)
        assertEquals(14, MusicDatabase.MIGRATION_13_14.endVersion)
    }

    @Test
    fun cachedTracksUseExplicitFourteenToFifteenAlbumLookupIndexesMigration() {
        assertEquals(14, MusicDatabase.MIGRATION_14_15.startVersion)
        assertEquals(15, MusicDatabase.MIGRATION_14_15.endVersion)
    }
    @Test
    fun registeredMigrationChainIsContiguousFromSchema6To20() {
        val versions = MusicDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((6 until 20).map { it to it + 1 }, versions)
    }

}
