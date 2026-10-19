package cn.miao.parttime.export

import cn.miao.parttime.data.DayBundle
import cn.miao.parttime.data.ShiftKind
import cn.miao.parttime.domain.Money
import cn.miao.parttime.domain.ShiftTotals
import cn.miao.parttime.domain.calculateDay
import cn.miao.parttime.domain.calculateShift
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ReportScope(val label: String) { NOON("中午"), EVENING("晚上"), DAY("整天") }

data class ReportAmount(val count: Int, val unitCents: Long, val totalCents: Long)
data class ReportOvertime(val description: String, val amount: ReportAmount) {
    val totalCents: Long get() = amount.totalCents
}
data class ReportTask(val task: String, val wages: List<ReportAmount>, val overtime: List<ReportOvertime>)
data class ReportShift(val kind: ShiftKind, val tasks: List<ReportTask>, val totals: ShiftTotals)
data class ReportModel(
    val location: String,
    val dateLabel: String,
    val scope: ReportScope,
    val headFeeCents: Long,
    val shifts: List<ReportShift>,
    val totals: ShiftTotals,
)

enum class ReportLineKind { TITLE, DATE, SHIFT, BODY, SUBTOTAL, SUMMARY, TOTAL }
data class ReportLine(val text: String, val kind: ReportLineKind = ReportLineKind.BODY)

/** Consumes one saved snapshot; personal names and telephone numbers never enter the report. */
fun buildReport(day: DayBundle, scope: ReportScope): ReportModel {
    val rate = requireNotNull(day.day.headFeeCents) { "请先在当天详情填写人头费单价；不收人费可填写0" }
    require(rate >= 0) { "人头费单价不能为负数" }
    require(day.people.all { it.person.shiftKind in ShiftKind.entries.map(ShiftKind::name) }) { "存在无法识别的班次，请先检查记录" }
    val kinds = when (scope) {
        ReportScope.NOON -> listOf(ShiftKind.NOON)
        ReportScope.EVENING -> listOf(ShiftKind.EVENING)
        ReportScope.DAY -> ShiftKind.entries.toList()
    }
    val shifts = kinds.map { kind ->
        val people = day.people.filter { it.person.shiftKind == kind.name }
            .sortedWith(compareBy({ it.person.sortOrder }, { it.person.id }))
        require(people.all { it.person.baseAmountCents >= 0 && it.overtime.all { item -> item.amountCents >= 0 } }) { "费用不能为负数" }
        val tasks = people.groupBy { it.person.task }.map { (task, members) ->
            val wages = members.groupBy { it.person.baseAmountCents }.map { (unit, entries) ->
                ReportAmount(entries.size, unit, Math.multiplyExact(entries.size.toLong(), unit))
            }
            val overtime = members.flatMap { member -> member.overtime.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
                .groupBy { it.description to it.amountCents }.map { (key, entries) ->
                    ReportOvertime(key.first, ReportAmount(entries.size, key.second, Math.multiplyExact(entries.size.toLong(), key.second)))
                }
            ReportTask(task, wages, overtime)
        }
        val totals = calculateShift(day, kind)
        val groupedBase = exactSum(tasks.flatMap { it.wages }.map { it.totalCents })
        val groupedOvertime = exactSum(tasks.flatMap { it.overtime }.map { it.totalCents })
        check(groupedBase == totals.baseCents && groupedOvertime == totals.overtimeCents) { "费用分组校验失败，请重新打开记录后导出" }
        ReportShift(kind, tasks, totals)
    }
    val totals = if (scope == ReportScope.DAY) calculateDay(day) else shifts.single().totals
    check(exactSum(shifts.map { requireNotNull(it.totals.totalCents) }) == totals.totalCents) { "费用汇总校验失败" }
    return ReportModel(
        location = day.day.locationName,
        dateLabel = LocalDate.parse(day.day.localDate).format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
        scope = scope,
        headFeeCents = rate,
        shifts = shifts,
        totals = totals,
    )
}

fun reportLines(report: ReportModel): List<ReportLine> = buildList {
    add(ReportLine(report.location, ReportLineKind.TITLE))
    add(ReportLine("${report.dateLabel} · ${report.scope.label}费用明细", ReportLineKind.DATE))
    for (shift in report.shifts) {
        add(ReportLine(shift.kind.label, ReportLineKind.SHIFT))
        for (task in shift.tasks) {
            task.wages.forEach { add(ReportLine("${task.task}：${equation(it, "人")}")) }
            task.overtime.forEach { add(ReportLine("${task.task} ${it.description}：${equation(it.amount, "人")}")) }
        }
        add(ReportLine("人费：${shift.totals.count}人 × ${amount(report.headFeeCents)} = ${amount(requireNotNull(shift.totals.feeCents))}"))
        add(ReportLine("${shift.kind.label}合计：${amount(requireNotNull(shift.totals.totalCents))}", ReportLineKind.SUBTOTAL))
    }
    if (report.scope == ReportScope.DAY) {
        add(ReportLine("当天费用汇总", ReportLineKind.SUMMARY))
        add(ReportLine("工资：${amount(report.totals.baseCents)}"))
        add(ReportLine("加班：${amount(report.totals.overtimeCents)}"))
        add(ReportLine("人费：${amount(requireNotNull(report.totals.feeCents))}"))
        val formula = report.shifts.joinToString(" + ") { amount(requireNotNull(it.totals.totalCents)) }
        add(ReportLine("总计：$formula = ${amount(requireNotNull(report.totals.totalCents))}", ReportLineKind.TOTAL))
    }
}

private fun equation(group: ReportAmount, unit: String) = "${group.count}$unit × ${amount(group.unitCents)} = ${amount(group.totalCents)}"
private fun amount(cents: Long) = Money.format(cents).trimEnd('0').trimEnd('.')
private fun exactSum(amounts: List<Long>) = amounts.fold(0L, Math::addExact)
