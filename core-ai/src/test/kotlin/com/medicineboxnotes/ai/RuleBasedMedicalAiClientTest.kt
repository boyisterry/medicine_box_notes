package com.medicineboxnotes.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlinx.coroutines.flow.toList

class RuleBasedMedicalAiClientTest {
    @Test fun scanLabelIsNeverMedicineName() = runBlocking {
        assertNull(RuleBasedMedicalAiClient().extractMedicine("药盒正面\n批准文号 123").name)
    }
    @Test fun extractsBasicMedicine() = runBlocking {
        val result = RuleBasedMedicalAiClient().extractMedicine("阿莫西林胶囊\n每次 0.5g\n每日三次\n疗程 7 天")
        assertEquals("阿莫西林胶囊", result.name)
        assertEquals(7, result.durationDays)
    }
    @Test fun queryDoesNotExposeInternalUuid() = runBlocking {
        val events = RuleBasedMedicalAiClient().answerStreaming("阿莫西林", "[Medicine 123e4567-e89b-12d3-a456-426614174000] 阿莫西林 库存3").toList()
        val answer = (events.last() as AiStreamEvent.Finished).answer.answer
        assertFalse(answer.contains("123e4567"))
    }
}
