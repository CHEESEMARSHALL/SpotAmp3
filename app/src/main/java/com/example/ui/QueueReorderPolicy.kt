package com.example.ui

data class QueueMoveStep(val fromIndex: Int, val toIndex: Int)

data class QueueDragResult(
    val moves: List<QueueMoveStep>,
    val remainingOffsetPx: Float
)

object QueueReorderPolicy {
    fun consumeDrag(
        startIndex: Int,
        accumulatedOffsetPx: Float,
        thresholdPx: Float,
        queueSize: Int
    ): QueueDragResult {
        if (queueSize < 2 || startIndex !in 0 until queueSize || thresholdPx <= 0f) {
            return QueueDragResult(emptyList(), 0f)
        }

        val moves = mutableListOf<QueueMoveStep>()
        var currentIndex = startIndex
        var remainingOffset = accumulatedOffsetPx

        while (remainingOffset >= thresholdPx) {
            if (currentIndex >= queueSize - 1) {
                remainingOffset = 0f
                break
            }
            val nextIndex = currentIndex + 1
            moves += QueueMoveStep(currentIndex, nextIndex)
            currentIndex = nextIndex
            remainingOffset -= thresholdPx
        }

        while (remainingOffset <= -thresholdPx) {
            if (currentIndex <= 0) {
                remainingOffset = 0f
                break
            }
            val nextIndex = currentIndex - 1
            moves += QueueMoveStep(currentIndex, nextIndex)
            currentIndex = nextIndex
            remainingOffset += thresholdPx
        }

        return QueueDragResult(moves, remainingOffset)
    }
}
