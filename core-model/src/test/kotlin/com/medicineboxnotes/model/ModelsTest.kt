package com.medicineboxnotes.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelsTest {
    @Test fun stockEstimateUnderstandsChineseFrequency() {
        assertEquals(21, MedicineItem(name = "药", frequency = "每日三次", durationDays = 7).estimatedStock())
    }

    @Test fun scheduleHonorsDateRange() {
        val today = LocalDate.of(2026, 7, 18)
        val item = MedicineItem(
            name = "药", isActive = true, scheduledTimes = listOf("09:00"),
            planStartEpochDay = today.minusDays(1).toEpochDay(),
            planEndEpochDay = today.plusDays(1).toEpochDay(),
        )
        assertTrue(item.isScheduledOn(today))
        assertFalse(item.isScheduledOn(today.plusDays(2)))
    }
}
