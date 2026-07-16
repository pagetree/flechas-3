package com.asrys.arrowgame

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class PuzzleSeedsResponse(val seeds: List<Int>)
data class StatsRequest(val seed: Int, val time: Double, val device_id: String?, val email: String? = null)
data class SaveProgressRequest(val device_id: String, val puzzle_number: Int, val email: String? = null)
data class CheckPlayerEmailRequest(val email: String, val website: String = "")
data class CheckPlayerEmailResponse(val exists: Boolean)
data class CreatePlayerRequest(
    val email: String,
    val player_name: String,
    val website: String = "",
    val device_id: String? = null
)
data class ProgressResponse(
    val device_id: String,
    val current_puzzle_number: Int,
    val max_puzzle_number: Int,
    val found: Boolean
)
data class SuccessResponse(val success: Boolean)

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
        // IMPORTANT: Replace this with your actual Railway URL!
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
