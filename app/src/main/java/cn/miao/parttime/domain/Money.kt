package cn.miao.parttime.domain

import cn.miao.parttime.data.DayBundle
import cn.miao.parttime.data.ShiftKind
import cn.miao.parttime.data.PersonBundle
import java.math.BigDecimal

object Money {
    private val inputPattern = Regex("[0-9]+(?:\\.[0-9]{1,2})?")

    fun parse(text: String): Long {
        val input = text.trim()
        require(inputPattern.matches(input)) { "请填写非负金额，最多保留两位小数" }
        return try {
            BigDecimal(input).movePointRight(2).longValueExact()
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("金额超出可保存范围", error)
        }
    }

    fun format(cents: Long): String {
        require(cents >= 0) { "金额不能为负数" }
        return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }
}

data class ShiftTotals(
    val count: Int,
    val baseCents: Long,
    val overtimeCents: Long,
    val feeCents: Long?,
    val totalCents: Long?,
)

fun calculateShift(day: DayBundle, shift: ShiftKind): ShiftTotals =
    totals(day.people.filter { it.person.shiftKind == shift.name }, day.day.headFeeCents)

fun calculateDay(day: DayBundle): ShiftTotals {
    val noon = calculateShift(day, ShiftKind.NOON)
    val evening = calculateShift(day, ShiftKind.EVENING)
    val base = Math.addExact(noon.baseCents, evening.baseCents)
    val overtime = Math.addExact(noon.overtimeCents, evening.overtimeCents)
    val known = Math.addExact(base, overtime)
    val fee = if (noon.feeCents != null && evening.feeCents != null) Math.addExact(noon.feeCents, evening.feeCents) else null
    return ShiftTotals(Math.addExact(noon.count, evening.count), base, overtime, fee, fee?.let { Math.addExact(known, it) })
}

private fun totals(people: List<PersonBundle>, rate: Long?): ShiftTotals {
    var base = 0L
    var overtime = 0L
    for (person in people) {
        base = Math.addExact(base, person.person.baseAmountCents)
        for (item in person.overtime) overtime = Math.addExact(overtime, item.amountCents)
    }
    val known = Math.addExact(base, overtime)
    val fee = rate?.let { Math.multiplyExact(people.size.toLong(), it) }
    return ShiftTotals(people.size, base, overtime, fee, fee?.let { Math.addExact(known, it) })
}
