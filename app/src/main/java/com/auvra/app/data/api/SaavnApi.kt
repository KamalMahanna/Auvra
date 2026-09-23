package com.auvra.app.data.api

import com.auvra.app.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SaavnApi {
    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiResponse<SearchSongResult>

    @GET("api/search/artists")
    suspend fun searchArtists(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiResponse<SearchArtistResult>

    @GET("api/search/albums")
    suspend fun searchAlbums(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiResponse<SearchAlbumResult>

    @GET("api/search/playlists")
    suspend fun searchPlaylists(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiResponse<SearchPlaylistResult>


    @GET("api/songs/{id}")
    suspend fun getSongById(@Path("id") id: String): ApiResponse<List<Song>>

    @GET("api/songs/{id}/suggestions")
    suspend fun getSongSuggestions(
        @Path("id") songId: String,
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<Song>>

    @GET("api/artists/{id}")
    suspend fun getArtistById(
        @Path("id") id: String,
        @Query("songCount") songCount: Int = 50,
        @Query("albumCount") albumCount: Int = 50
    ): ApiResponse<ArtistDetail>

    @GET("api/albums")
    suspend fun getAlbumById(@Query("id") id: String): ApiResponse<Album>

    @GET("api/playlists")
    suspend fun getPlaylistById(
        @Query("id") id: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 1000
    ): ApiResponse<Playlist>

    @GET("api/modules")
    suspend fun getModules(): ApiResponse<List<ModuleSection>>
}
