package cn.miao.parttime.domain

import cn.miao.parttime.data.Assignment
import cn.miao.parttime.data.DayBundle
import cn.miao.parttime.data.DayRecord
import cn.miao.parttime.data.OvertimeItem
import cn.miao.parttime.data.PersonBundle
import cn.miao.parttime.data.ShiftKind
import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun parsesExactCentsAndLargestLong() {
        assertEquals(0L, Money.parse("0"))
        assertEquals(10L, Money.parse("0.10"))
        assertEquals(20L, Money.parse(" 0.2 "))
        assertEquals(100L, Money.parse("01"))
        assertEquals(Long.MAX_VALUE, Money.parse("92233720368547758.07"))
        assertEquals("92233720368547758.07", Money.format(Long.MAX_VALUE))
        assertEquals("0.30", Money.format(Math.addExact(Money.parse("0.10"), Money.parse("0.20"))))
    }

    @Test fun rejectsMissingNegativeFractionExponentAndOverflow() {
        listOf("", " ", "-1", "+1", ".1", "1.", "1.001", "1e2", "NaN", "1,000", "92233720368547758.08").forEach {
            assertThrows("input: $it", IllegalArgumentException::class.java) { Money.parse(it) }
        }
    }

    @Test fun unsetRateStaysUnknownAndExplicitZeroCompletesTotal() {
        val day = fixture(null, listOf(person(1, ShiftKind.NOON, 10, 20)))
        assertEquals(ShiftTotals(1, 10, 20, null, null), calculateDay(day))
        assertEquals(ShiftTotals(1, 10, 20, 0, 30), calculateDay(day.copy(day = day.day.copy(headFeeCents = 0))))
    }

    @Test fun sameNameInTwoShiftsCountsTwiceAndOvertimeDoesNotAddPeople() {
        val day = fixture(500, listOf(person(1, ShiftKind.NOON, 10000, 2000, 4000), person(2, ShiftKind.EVENING, 0)))
        assertEquals(ShiftTotals(1, 10000, 6000, 500, 16500), calculateShift(day, ShiftKind.NOON))
        assertEquals(ShiftTotals(2, 10000, 6000, 1000, 17000), calculateDay(day))
        assertEquals(ShiftTotals(0, 0, 0, 0, 0), calculateDay(fixture(500, emptyList())))
    }

    @Test fun referenceCaseIsComputedFromTwentyFiveAssignments() {
        val noon = (1L..11L).map { person(it, ShiftKind.NOON, if (it <= 3) 10000 else 8000, if (it <= 2) 4000 else 0) }
        val evening = (12L..25L).map { person(it, ShiftKind.EVENING, if (it <= 14) 10000 else 8000, if (it <= 16) 2000 else 0) }
        val day = fixture(500, noon + evening)
        assertEquals(107500L, calculateShift(day, ShiftKind.NOON).totalCents)
        assertEquals(135000L, calculateShift(day, ShiftKind.EVENING).totalCents)
        assertEquals(ShiftTotals(25, 212000, 18000, 12500, 242500), calculateDay(day))
    }

    @Test fun refusesSumMultiplicationAndCrossShiftOverflow() {
        assertThrows(ArithmeticException::class.java) { calculateDay(fixture(null, listOf(person(1, ShiftKind.NOON, Long.MAX_VALUE, 1)))) }
        assertThrows(ArithmeticException::class.java) { calculateDay(fixture(Long.MAX_VALUE, listOf(person(1, ShiftKind.NOON, 0), person(2, ShiftKind.NOON, 0)))) }
        assertThrows(ArithmeticException::class.java) { calculateDay(fixture(0, listOf(person(1, ShiftKind.NOON, Long.MAX_VALUE), person(2, ShiftKind.EVENING, 1)))) }
    }

    private fun fixture(rate: Long?, people: List<PersonBundle>) = DayBundle(DayRecord(1, "上海", "上海", "2026-10-19", rate), people)
    private fun person(id: Long, shift: ShiftKind, base: Long, vararg overtime: Long) = PersonBundle(
        Assignment(id, 1, shift.name, "张三", "", "服务员", base, id.toInt()),
        overtime.mapIndexed { index, cents -> OvertimeItem(index.toLong(), id, "加班", cents, index) },
    )
}
