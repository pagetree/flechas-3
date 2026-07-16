package com.asrys.arrowgame

import android.app.Application
import android.provider.Settings
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import android.view.Choreographer
import kotlin.coroutines.resume

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val api = GameApi.create()
    private val seedPool = mutableListOf<Int>()
    private val appContext = getApplication<Application>()
    private val onboardingPrefs by lazy {
        appContext.getSharedPreferences("arrow_onboarding_prefs", Context.MODE_PRIVATE)
    }
    private val deviceId: String by lazy {
        Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    }
    private fun currentPlayerEmail(): String? {
        val value = onboardingPrefs.getString("player_email", null)?.trim()?.lowercase()
        return if (value.isNullOrBlank()) null else value
    }

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    // ── Single master animation loop ──────────────────────────────────────────
    // Tracks per-arrow progress independently of GameState so we can batch
    // all updates into a SINGLE state write per frame (no jitter / flooding).
    private data class ArrowAnim(
        val id: Int,
        var progress: Float,
        val maxProgress: Float,
        val isObstructed: Boolean
    )
    private val activeAnims = mutableMapOf<Int, ArrowAnim>()
    private var gameLoopJob: Job? = null
    private val SPEED = 18.0f  // cells per second — tweak here

    private suspend fun awaitFrame(): Long = suspendCancellableCoroutine { cont ->
        Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
            if (cont.isActive) cont.resume(frameTimeNanos)
        }
    }

    private fun ensureGameLoop() {
        if (gameLoopJob?.isActive == true) return
        gameLoopJob = viewModelScope.launch {
            var lastTime = -1L
            while (isActive) {
                // awaitFrame() syncs EXACTLY with the display's VSync signal — zero jitter
                val frameTimeNanos = awaitFrame()
                if (lastTime < 0L) {
                    lastTime = frameTimeNanos
                    continue
                }
                val dt = ((frameTimeNanos - lastTime) / 1_000_000_000f).coerceAtMost(0.05f)
                lastTime = frameTimeNanos

                if (activeAnims.isEmpty()) {
                    gameLoopJob = null
                    return@launch
                }

                val finished = mutableListOf<ArrowAnim>()
                for (anim in activeAnims.values) {
                    anim.progress = (anim.progress + SPEED * dt).coerceAtMost(anim.maxProgress)
                    if (anim.progress >= anim.maxProgress) finished.add(anim)
                }

                val puzzleId = _state.value.puzzle?.id ?: continue

                val progressSnapshot = activeAnims.values.associate { it.id to it.progress }
                val obstructedFinished = finished.filter { it.isObstructed }
                val exitFinished = finished.filter { !it.isObstructed }

                for (a in finished) activeAnims.remove(a.id)

                _state.update { state ->
                    if (state.puzzle?.id != puzzleId) return@update state

                    var newMoving = state.movingArrows.map { m ->
                        val p = progressSnapshot[m.id]
                        if (p != null) m.copy(progressCells = p) else m
                    }

                    var newRemaining = state.remaining
                    var newLives = state.lives
                    var newLastBlocked = state.lastBlockedArrowId
                    var newCollision = state.collisionTrigger
                    var newGameOver = state.isGameOver

                    if (exitFinished.isNotEmpty()) {
                        val exitIds = exitFinished.map { it.id }.toSet()
                        newRemaining = newRemaining.filterNot { it.id in exitIds }
                        newMoving = newMoving.filterNot { it.id in exitIds }
                    }

                    if (obstructedFinished.isNotEmpty()) {
                        val obsIds = obstructedFinished.map { it.id }.toSet()
                        newMoving = newMoving.filterNot { it.id in obsIds }
                        newLives = (newLives - obstructedFinished.size).coerceAtLeast(0)
                        newLastBlocked = obstructedFinished.lastOrNull()?.id
                        newCollision = newCollision + obstructedFinished.size
                        newGameOver = newLives == 0
                    }

                    state.copy(
                        movingArrows = newMoving,
                        remaining = newRemaining,
                        lives = newLives,
                        lastBlockedArrowId = newLastBlocked,
                        collisionTrigger = newCollision,
                        isGameOver = newGameOver,
                        isLevelComplete = newRemaining.isEmpty() && !newGameOver
                    )
                }
            }
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    init {
        fetchSeeds()
        restoreProgressAndStart()
    }

    private fun fetchSeeds() {
        viewModelScope.launch {
            try {
                val response = api.getPuzzles(20)
                seedPool.addAll(response.seeds)
                Log.d("ArrowGame", "Successfully fetched ${response.seeds.size} seeds from API")
            } catch (e: Exception) {
                val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                Log.e("ArrowGame", "Failed to fetch seeds: ${e.message}")
                if (errorBody != null) Log.e("ArrowGame", "Server Error Body: $errorBody")
            }
        }
    }

    private fun getNextSeed(): Int {
        if (seedPool.size < 5) fetchSeeds()
        return if (seedPool.isNotEmpty()) seedPool.removeAt(0) else kotlin.random.Random.nextInt()
    }

    fun onArrowTap(arrowId: Int, scale: Float = 1f) {
        val current = _state.value
        if (current.isGameOver || current.isLevelComplete) return
        if (activeAnims.containsKey(arrowId)) return  // already animating

        val level = current.puzzle ?: return
        val arrow = current.remaining.firstOrNull { it.id == arrowId } ?: return

        val collisionDistance = getCollisionDistance(level, arrow, current.remaining, current.movingArrows)

        if (collisionDistance != null) {
            scheduleArrowAnim(level, arrow, collisionDistance, obstructed = true)
        } else {
            scheduleArrowAnim(level, arrow, computeExitProgress(level, arrow), obstructed = false)
        }
    }

    private fun scheduleArrowAnim(level: LevelMask, arrow: ArrowPiece, maxProgress: Float, obstructed: Boolean) {
        val puzzleId = level.id

        // Register in tracking map
        activeAnims[arrow.id] = ArrowAnim(
            id = arrow.id,
            progress = 0f,
            maxProgress = maxProgress,
            isObstructed = obstructed
        )

        // Add to movingArrows list in state so the UI renders the animation
        _state.update { state ->
            if (state.movingArrows.any { it.id == arrow.id }) state
            else state.copy(
                movingArrows = state.movingArrows + MovingArrowState(
                    id = arrow.id,
                    progressCells = 0f,
                    maxProgressCells = maxProgress,
                    isObstructed = obstructed
                ),
                lastBlockedArrowId = null
            )
        }

        ensureGameLoop()
    }

    fun resumeWithOneLife() {
        _state.update {
            it.copy(lives = 1, isGameOver = false)
        }
    }

    fun submitStats(timeSeconds: Int) {
        val seed = _state.value.currentSeed ?: return
        viewModelScope.launch {
            try {
                api.submitStats(StatsRequest(seed, timeSeconds.toDouble(), deviceId, currentPlayerEmail()))
                Log.d("ArrowGame", "Successfully submitted stats for seed $seed")
            } catch (e: Exception) {
                val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                Log.e("ArrowGame", "Failed to submit stats: ${e.message}")
                if (errorBody != null) Log.e("ArrowGame", "Server Error Body: $errorBody")
            }
        }
    }

    fun nextPuzzle() {
        activeAnims.clear()
        val finishedPuzzleNumber = _state.value.puzzleNumber
        val nextNumber = finishedPuzzleNumber + 1
        val seed = getNextSeed()
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = nextNumber, seed = seed)
        _state.update {
            it.copy(
                puzzleNumber = nextNumber,
                puzzle = puzzle,
                currentSeed = seed,
                remaining = puzzle.arrows,
                movingArrows = emptyList(),
                lastBlockedArrowId = null,
                lives = 3,
                collisionTrigger = 0,
                isGameOver = false,
                isLevelComplete = false
            )
        }
        persistProgress(finishedPuzzleNumber)
    }

    fun startRandomPuzzle() {
        activeAnims.clear()
        val currentPuzzleNumber = _state.value.puzzleNumber
        val seed = getNextSeed()
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = currentPuzzleNumber, seed = seed)
        _state.update {
            it.copy(
                puzzle = puzzle,
                currentSeed = seed,
                remaining = puzzle.arrows,
                movingArrows = emptyList(),
                lastBlockedArrowId = null,
                lives = 3,
                collisionTrigger = 0,
                isGameOver = false,
                isLevelComplete = false
            )
        }
    }

    private fun restoreProgressAndStart() {
        viewModelScope.launch {
            val remotePuzzleNumber = loadRemotePuzzleNumber()
            _state.update { it.copy(
                puzzleNumber = remotePuzzleNumber,
                isLoadingProgress = false
            ) }
            startRandomPuzzle()
        }
    }

    private suspend fun loadRemotePuzzleNumber(): Int {
        if (deviceId.isBlank()) return 1
        return try {
            val response = api.getProgress(deviceId)
            maxOf(1, response.current_puzzle_number, response.max_puzzle_number)
        } catch (e: Exception) {
            val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
            Log.e("ArrowGame", "Failed to load progress: ${e.message}")
            if (errorBody != null) Log.e("ArrowGame", "Server Error Body: $errorBody")
            1
        }
    }

    private fun persistProgress(puzzleNumber: Int) {
        if (deviceId.isBlank()) return
        viewModelScope.launch {
            try {
                api.saveProgress(SaveProgressRequest(deviceId, puzzleNumber, currentPlayerEmail()))
                Log.d("ArrowGame", "Saved progress at puzzle $puzzleNumber")
            } catch (e: Exception) {
                val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                Log.e("ArrowGame", "Failed to save progress: ${e.message}")
                if (errorBody != null) Log.e("ArrowGame", "Server Error Body: $errorBody")
            }
        }
    }

    fun resetPuzzle() {
        activeAnims.clear()
        val current = _state.value
        val seed = current.currentSeed ?: getNextSeed()
        val puzzle = LevelRepository.generatePuzzle(puzzleNumber = current.puzzleNumber, seed = seed)
        _state.update {
            it.copy(
                puzzle = puzzle,
                currentSeed = seed,
                remaining = puzzle.arrows,
                movingArrows = emptyList(),
                lastBlockedArrowId = null,
                lives = 3,
                collisionTrigger = 0,
                isGameOver = false,
                isLevelComplete = false
            )
        }
    }

    private fun getCollisionDistance(level: LevelMask, arrow: ArrowPiece, all: List<ArrowPiece>, moving: List<MovingArrowState>): Float? {
        val movingIds = moving.map { it.id }.toSet()
        val occupied = all
            .filterNot { it.id == arrow.id || movingIds.contains(it.id) }
            .flatMap { other -> if (other.path.isNotEmpty()) other.path else listOf(other.start) }
            .toHashSet()
        val origin = arrow.path.lastOrNull() ?: arrow.start
        var x = origin.x + arrow.direction.dx
        var y = origin.y + arrow.direction.dy
        var distance = 1
        while (x >= 0 && y >= 0 && x < level.width && y < level.height) {
            if (occupied.contains(Cell(x, y))) return distance.toFloat() - 0.7f
            x += arrow.direction.dx
            y += arrow.direction.dy
            distance++
        }
        return null
    }

    private fun computeExitProgress(level: LevelMask, arrow: ArrowPiece): Float {
        val cells = if (arrow.path.isNotEmpty()) arrow.path else listOf(arrow.start)
        var maxSteps = 0
        for (cell in cells) {
            val steps = when (arrow.direction) {
                Direction.RIGHT -> level.width - cell.x
                Direction.LEFT  -> cell.x + 1
                Direction.DOWN  -> level.height - cell.y
                Direction.UP    -> cell.y + 1
            }
            if (steps > maxSteps) maxSteps = steps
        }
        val offscreenMarginCells = kotlin.math.max(level.width, level.height).toFloat() + 5f
        return maxSteps.toFloat() + offscreenMarginCells
    }
}
