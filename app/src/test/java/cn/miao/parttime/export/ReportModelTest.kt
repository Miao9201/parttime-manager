package cn.miao.parttime.export

import cn.miao.parttime.data.*
import org.junit.Assert.*
import org.junit.Test

class ReportModelTest {
    @Test fun referenceBillIncludesAllTasksFeesAndDaySummary() {
        val noon = (0..10).map { i -> person(i + 1L, ShiftKind.NOON, when (i) { 0, 1 -> "洗碗"; 2 -> "打荷"; in 3..6 -> "传菜"; else -> "服务员" }, if (i < 3) 10000 else 8000,
            if (i >= 7) listOf("加班1小时" to 2000L) else emptyList()) }
        val evening = (0..13).map { i -> person(i + 20L, ShiftKind.EVENING, when (i) { 0, 1 -> "洗碗"; 2 -> "打荷"; in 3..6 -> "传菜"; else -> "服务员" }, if (i < 3) 10000 else 8000,
            when (i) { 0, 1 -> listOf("加班2小时" to 4000L); 2 -> listOf("加班1小时" to 2000L); else -> emptyList() }) }
        val model = buildReport(day(500, noon + evening), ReportScope.DAY)
        assertEquals("旺辉完整地点", model.location)
        assertEquals("2026年10月6日", model.dateLabel)
        assertEquals(listOf(107500L, 135000L), model.shifts.map { it.totals.totalCents })
        assertEquals(242500L, model.totals.totalCents)
        assertEquals(212000L, model.totals.baseCents)
        assertEquals(18000L, model.totals.overtimeCents)
        assertEquals(12500L, model.totals.feeCents)
        val lines = reportLines(model).map { it.text }
        assertTrue(lines.contains("洗碗：2人 × 100 = 200"))
        assertTrue(lines.contains("服务员 加班1小时：4人 × 20 = 80"))
        assertTrue(lines.contains("人费：11人 × 5 = 55"))
        assertTrue(lines.contains("总计：1075 + 1350 = 2425"))
        assertFalse(lines.joinToString().contains("私密姓名"))
        assertFalse(lines.joinToString().contains("PHONE_TEST_ONLY"))
    }

    @Test fun stableFirstRegistrationGroupsDifferentWagesAndOvertimeByTaskContentAndAmount() {
        val people = listOf(
            person(9, ShiftKind.NOON, "服务员", 8000, listOf("收尾" to 2000L, "收尾" to 3000L, "清理" to 2000L)).withOrder(0),
            person(3, ShiftKind.NOON, "传菜", 9000, listOf("收尾" to 2000L)).withOrder(1),
            person(4, ShiftKind.NOON, "服务员", 10000, listOf("收尾" to 2000L)).withOrder(2),
            person(2, ShiftKind.NOON, "服务员", 8000, listOf("收尾" to 2000L)).withOrder(3),
        )
        val model = buildReport(day(500, people.reversed().map { it.copy(overtime = it.overtime.reversed()) }), ReportScope.NOON)
        assertEquals(listOf("服务员", "传菜"), model.shifts.single().tasks.map { it.task })
        val task = model.shifts.single().tasks.first()
        assertEquals(listOf(8000L, 10000L), task.wages.map { it.unitCents })
        assertEquals(listOf(2, 1), task.wages.map { it.count })
        assertEquals(listOf(6000L, 3000L, 2000L), task.overtime.map { it.totalCents })
        assertEquals(listOf(2000L), model.shifts.single().tasks.last().overtime.map { it.totalCents })
        assertEquals(4, model.totals.count)
        assertEquals(2000L, model.totals.feeCents)
        assertEquals(50000L, model.totals.totalCents)
        val lines = reportLines(model).map { it.text }
        assertTrue(lines.indexOf("服务员 清理：1人 × 20 = 20") < lines.indexOf("传菜：1人 × 90 = 90"))
    }

    @Test fun allScopesKeepExactCentsAndOnlyTheirOwnShift() {
        val fixture = day(0, listOf(person(1, ShiftKind.NOON, "甲", 10, listOf("整理" to 20L)), person(2, ShiftKind.EVENING, "乙", 7)))
        assertEquals(30L, buildReport(fixture, ReportScope.NOON).totals.totalCents)
        assertEquals(7L, buildReport(fixture, ReportScope.EVENING).totals.totalCents)
        assertEquals(37L, buildReport(fixture, ReportScope.DAY).totals.totalCents)
        assertEquals(listOf(ShiftKind.NOON), buildReport(fixture, ReportScope.NOON).shifts.map { it.kind })
        assertEquals(listOf(ShiftKind.EVENING), buildReport(fixture, ReportScope.EVENING).shifts.map { it.kind })
        assertFalse(reportLines(buildReport(fixture, ReportScope.NOON)).joinToString().contains("乙"))
        assertTrue(reportLines(buildReport(fixture, ReportScope.DAY)).any { it.text == "总计：0.3 + 0.07 = 0.37" })
    }

    @Test fun missingFeeBlocksEveryScopeButExplicitZeroAllowsEmptyBill() {
        ReportScope.entries.forEach { scope ->
            assertThrows(IllegalArgumentException::class.java) { buildReport(day(null, emptyList()), scope) }
            assertEquals(0L, buildReport(day(0, emptyList()), scope).totals.totalCents)
        }
    }

    @Test fun preservesLongTextAndRejectsArithmeticOverflow() {
        val task = "很长的任务内容".repeat(80)
        val description = "很长的加班内容".repeat(100)
        val model = buildReport(day(0, listOf(person(1, ShiftKind.NOON, task, 10, listOf(description to 20L)))), ReportScope.DAY)
        val text = reportLines(model).joinToString { it.text }
        assertTrue(text.contains(task))
        assertTrue(text.contains(description))
        assertThrows(ArithmeticException::class.java) {
            buildReport(day(0, listOf(person(1, ShiftKind.NOON, "甲", Long.MAX_VALUE), person(2, ShiftKind.EVENING, "乙", 1))), ReportScope.DAY)
        }
    }

    private fun day(rate: Long?, people: List<PersonBundle>) = DayBundle(DayRecord(1, "旺辉完整地点", "旺辉完整地点", "2026-10-06", rate), people)
    private fun person(id: Long, shift: ShiftKind, task: String, amount: Long, items: List<Pair<String, Long>> = emptyList()) = PersonBundle(
        Assignment(id, 1, shift.name, "私密姓名", "PHONE_TEST_ONLY", task, amount, id.toInt()),
        items.mapIndexed { i, item -> OvertimeItem(i.toLong(), id, item.first, item.second, i) },
    )
    private fun PersonBundle.withOrder(order: Int) = copy(person = person.copy(sortOrder = order))
}
