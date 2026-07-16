package com.asrys.arrowgame

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0)
}

enum class ArrowBend {
    STRAIGHT,
    LEFT_90,
    RIGHT_90
}

data class Cell(val x: Int, val y: Int)

data class ArrowPiece(
    val id: Int,
    val start: Cell,
    val direction: Direction,
    val tailFactor: Float,
    val bend: ArrowBend = ArrowBend.STRAIGHT,
    val path: List<Cell> = emptyList()
)

data class LevelMask(
    val id: String,
    val width: Int,
    val height: Int,
    val activeCells: Set<Cell>,
    val arrows: List<ArrowPiece>
)

data class GameState(
    val puzzleNumber: Int = 1,
    val lives: Int = 3,
    val puzzle: LevelMask? = null,
    val currentSeed: Int? = null,
    val remaining: List<ArrowPiece> = emptyList(),
    val movingArrows: List<MovingArrowState> = emptyList(),
    val lastBlockedArrowId: Int? = null,
    val collisionTrigger: Int = 0,
    val isGameOver: Boolean = false,
    val isLevelComplete: Boolean = false,
    val isLoadingProgress: Boolean = true  // true until server level is fetched
)

data class MovingArrowState(
    val id: Int,
    val progressCells: Float,
    val maxProgressCells: Float,
    val isObstructed: Boolean = false
)
