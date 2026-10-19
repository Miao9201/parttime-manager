package cn.miao.parttime.data

import androidx.room.withTransaction
import cn.miao.parttime.domain.Money
import cn.miao.parttime.domain.calculateDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class AppRepository(private val database: AppDatabase) {
    private val dao = database.dao()
    val days: Flow<List<DayBundle>> = dao.observeDays().map { days ->
        days.map { bundle ->
            bundle.copy(people = bundle.people.sortedWith(compareBy({ it.person.shiftKind }, { it.person.sortOrder }, { it.person.id }))
                .map { it.copy(overtime = it.overtime.sortedWith(compareBy({ item -> item.sortOrder }, { item -> item.id }))) })
        }
    }
    val locations: Flow<List<SavedLocation>> = dao.observeLocations()

    suspend fun saveDay(id: Long?, location: String, date: String): Long {
        val name = location.trim()
        require(name.isNotEmpty()) { "请填写地点名称" }
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
        require(parsedDate != null && parsedDate.toString() == date) { "请选择有效日期" }
        return database.withTransaction {
            val conflict = dao.findDay(name, date)
            if (conflict != null && conflict != id) throw DuplicateDay(conflict)
            val now = System.currentTimeMillis()
            val result = if (id == null) {
                dao.insertDay(DayRecord(locationName = name, locationKey = name, localDate = date, createdAt = now, updatedAt = now))
            } else {
                val existing = requireDay(id).day
                dao.updateDay(existing.copy(locationName = name, locationKey = name, localDate = date, updatedAt = now))
                id
            }
            dao.saveLocation(SavedLocation(name, name, now))
            validateTotals(result)
            result
        }
    }

    suspend fun setHeadFee(dayId: Long, text: String) {
        val amount = if (text.isBlank()) null else Money.parse(text)
        database.withTransaction {
            val day = requireDay(dayId).day
            dao.updateDay(day.copy(headFeeCents = amount, updatedAt = System.currentTimeMillis()))
            validateTotals(dayId)
        }
    }

    suspend fun deleteDay(dayId: Long) {
        database.withTransaction { dao.deleteDay(dayId) }
    }

    suspend fun savePerson(dayId: Long, shift: ShiftKind, id: Long?, draft: PersonDraft, allowDuplicate: Boolean = false): Long {
        val name = draft.name.trim()
        val phone = draft.phone.trim()
        val task = draft.task.trim()
        require(name.isNotEmpty()) { "请填写姓名" }
        require(task.isNotEmpty()) { "请填写工作任务" }
        val base = Money.parse(draft.amount)
        val overtime = draft.overtime.mapIndexed { index, item ->
            val description = item.description.trim()
            require(description.isNotEmpty()) { "请填写第${index + 1}条加班时长" }
            OvertimeItem(assignmentId = 0, description = description, amountCents = Money.parse(item.amount), sortOrder = index)
        }
        return database.withTransaction {
            val snapshot = requireDay(dayId)
            val original = if (id == null) null else requireNotNull(dao.person(id)) { "此人员记录已不存在" }
            require(original == null || (original.dayId == dayId && original.shiftKind == shift.name)) { "人员不属于当前班次" }
            val peers = snapshot.people.filter { it.person.shiftKind == shift.name }
            if (!allowDuplicate) {
                val duplicate = peers.firstOrNull {
                    it.person.id != id && it.person.name == name
                }
                if (duplicate != null) throw DuplicatePerson(duplicate.person.id)
            }
            val sortOrder = original?.sortOrder ?: Math.addExact(peers.maxOfOrNull { it.person.sortOrder } ?: -1, 1)
            val person = Assignment(id = id ?: 0, dayId = dayId, shiftKind = shift.name, name = name, phone = phone, task = task, baseAmountCents = base, sortOrder = sortOrder)
            val savedId = if (original == null) dao.insertPerson(person) else {
                dao.updatePerson(person)
                id!!
            }
            dao.deleteOvertime(savedId)
            dao.insertOvertime(overtime.map { it.copy(assignmentId = savedId) })
            touchDay(snapshot.day)
            validateTotals(dayId)
            savedId
        }
    }

    suspend fun deletePerson(id: Long) {
        database.withTransaction {
            val person = requireNotNull(dao.person(id)) { "此人员记录已不存在" }
            dao.deletePerson(id)
            touchDay(requireDay(person.dayId).day)
            validateTotals(person.dayId)
        }
    }

    suspend fun saveOvertime(personId: Long, itemId: Long?, draft: OvertimeDraft): Long {
        val description = draft.description.trim()
        require(description.isNotEmpty()) { "请填写加班时长" }
        val amount = Money.parse(draft.amount)
        return database.withTransaction {
            val person = requireNotNull(dao.person(personId)) { "此人员记录已不存在" }
            val day = requireDay(person.dayId)
            val original = itemId?.let { requireNotNull(dao.overtimeItem(it)) { "此加班记录已不存在" } }
            require(original == null || original.assignmentId == personId) { "加班记录不属于当前人员" }
            val order = original?.sortOrder ?: Math.addExact(
                day.people.first { it.person.id == personId }.overtime.maxOfOrNull { it.sortOrder } ?: -1, 1)
            val item = OvertimeItem(itemId ?: 0, personId, description, amount, order)
            val savedId = if (original == null) dao.insertOvertimeItem(item) else {
                dao.updateOvertimeItem(item)
                original.id
            }
            touchDay(day.day)
            validateTotals(person.dayId)
            savedId
        }
    }

    suspend fun deleteOvertime(personId: Long, itemId: Long) {
        database.withTransaction {
            val person = requireNotNull(dao.person(personId)) { "此人员记录已不存在" }
            val item = requireNotNull(dao.overtimeItem(itemId)) { "此加班记录已不存在" }
            require(item.assignmentId == personId) { "加班记录不属于当前人员" }
            dao.deleteOvertimeItem(itemId)
            touchDay(requireDay(person.dayId).day)
            validateTotals(person.dayId)
        }
    }

    private suspend fun requireDay(id: Long): DayBundle = requireNotNull(dao.day(id)) { "此地点日期记录已不存在" }
    private suspend fun validateTotals(id: Long) { calculateDay(requireDay(id)) }
    private suspend fun touchDay(day: DayRecord) { dao.updateDay(day.copy(updatedAt = System.currentTimeMillis())) }
}
