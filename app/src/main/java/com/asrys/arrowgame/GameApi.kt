package com.asrys.arrowgame

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class PuzzleSeedsResponse(
    @SerializedName("seeds") val seeds: List<Int>
)

data class StatsRequest(
    @SerializedName("seed") val seed: Int,
    @SerializedName("time") val time: Double,
    @SerializedName("device_id") val device_id: String?,
    @SerializedName("email") val email: String? = null
)

data class SaveProgressRequest(
    @SerializedName("device_id") val device_id: String,
    @SerializedName("puzzle_number") val puzzle_number: Int,
    @SerializedName("email") val email: String? = null
)

data class CheckPlayerEmailRequest(
    @SerializedName("email") val email: String,
    @SerializedName("website") val website: String = ""
)

data class CheckPlayerEmailResponse(
    @SerializedName("exists") val exists: Boolean
)

data class CreatePlayerRequest(
    @SerializedName("email") val email: String,
    @SerializedName("player_name") val player_name: String,
    @SerializedName("website") val website: String = "",
    @SerializedName("device_id") val device_id: String? = null
)

data class ProgressResponse(
    @SerializedName("device_id") val device_id: String,
    @SerializedName("current_puzzle_number") val current_puzzle_number: Int,
    @SerializedName("max_puzzle_number") val max_puzzle_number: Int,
    @SerializedName("found") val found: Boolean
)

data class SuccessResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("exists") val exists: Boolean = false
)

interface GameApi {
    @GET("index.php?action=get_puzzles")
    suspend fun getPuzzles(@Query("count") count: Int): PuzzleSeedsResponse

    @POST("index.php?action=submit_stats")
    suspend fun submitStats(@Body stats: StatsRequest): SuccessResponse

    @GET("index.php?action=get_progress")
    suspend fun getProgress(@Query("device_id") deviceId: String): ProgressResponse

    @POST("index.php?action=save_progress")
    suspend fun saveProgress(@Body payload: SaveProgressRequest): SuccessResponse

    @POST("index.php?action=check_player_email")
    suspend fun checkPlayerEmail(@Body payload: CheckPlayerEmailRequest): CheckPlayerEmailResponse

    @POST("index.php?action=create_player")
    suspend fun createPlayer(@Body payload: CreatePlayerRequest): SuccessResponse

    companion object {
        private const val BASE_URL = "https://arrow-game.up.railway.app/"

        fun create(): GameApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GameApi::class.java)
        }
    }
}
