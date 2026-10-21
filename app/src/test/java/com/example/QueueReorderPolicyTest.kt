package com.example

import com.example.ui.QueueMoveStep
import com.example.ui.QueueReorderPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueReorderPolicyTest {
    @Test
    fun largeDownwardDragMovesTheSameItemAcrossEachAdjacentSlot() {
        val result = QueueReorderPolicy.consumeDrag(
            startIndex = 1,
            accumulatedOffsetPx = 125f,
            thresholdPx = 50f,
            queueSize = 5
        )

        assertEquals(
            listOf(QueueMoveStep(1, 2), QueueMoveStep(2, 3)),
            result.moves
        )
        assertEquals(25f, result.remainingOffsetPx, 0f)
    }

    @Test
    fun dragAtQueueBoundaryDoesNotEmitInvalidMovesOrKeepBlockedOffset() {
        val result = QueueReorderPolicy.consumeDrag(
            startIndex = 3,
            accumulatedOffsetPx = 130f,
            thresholdPx = 50f,
            queueSize = 4
        )

        assertEquals(emptyList<QueueMoveStep>(), result.moves)
        assertEquals(0f, result.remainingOffsetPx, 0f)
    }

    @Test
    fun negativeDragUsesUpdatedIndexForEveryMove() {
        val result = QueueReorderPolicy.consumeDrag(
            startIndex = 3,
            accumulatedOffsetPx = -100f,
            thresholdPx = 50f,
            queueSize = 5
        )

        assertEquals(
            listOf(QueueMoveStep(3, 2), QueueMoveStep(2, 1)),
            result.moves
        )
        assertEquals(0f, result.remainingOffsetPx, 0f)
    }
}
