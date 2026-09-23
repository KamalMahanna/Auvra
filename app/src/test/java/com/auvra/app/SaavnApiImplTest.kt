package com.auvra.app

import com.auvra.app.data.api.SaavnApiImpl
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SaavnApiImplTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Test
    fun testPlaylistFetchingAndDeduplication() = runBlocking {
        val api = SaavnApiImpl(okHttpClient, moshi)

        // Fetch a known playlist (Cheer For India - Hindi)
        val response = api.getPlaylistById("946457594", 0, 50)

        assertTrue("API call should be successful", response.success)
        val playlist = response.data
        assertNotNull("Playlist data should not be null", playlist)
        assertNotNull("Playlist songs should not be null", playlist!!.songs)

        println("Playlist title: ${playlist.name}")
        println("Playlist song count after deduplication: ${playlist.songCount}")
        println("Songs list:")
        playlist.songs!!.forEachIndexed { index, song ->
            println("$index: ID=${song.id}, Name='${song.name}', Artist='${song.primaryArtistNames}'")
        }

        // Verify no duplicate combinations of name + artist exist
        val seen = mutableSetOf<Pair<String, String>>()
        playlist.songs!!.forEach { song ->
            val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
            val added = seen.add(key)
            assertTrue("Should not have duplicate song+artist: '${song.name}' by '${song.primaryArtistNames}'", added)
        }
    }

    @Test
    fun testGetModules() = runBlocking {
        val api = SaavnApiImpl(okHttpClient, moshi)
        val response = api.getModules()

        assertTrue("getModules API call should be successful", response.success)
        val sections = response.data
        assertNotNull("Sections should not be null", sections)
        assertFalse("Sections list should not be empty", sections!!.isEmpty())

        println("Total browse sections found: ${sections.size}")
        sections.forEach { section ->
            println("Section: '${section.title}' (source: '${section.source}'), Items count: ${section.data.size}")
            section.data.take(3).forEach { item ->
                println("  - Item: ID=${item.id}, Name='${item.name}', Type='${item.type}'")
            }
        }
    }
}
