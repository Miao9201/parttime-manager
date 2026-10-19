package cn.miao.parttime.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.miao.parttime.domain.calculateDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = AppRepository(database)
    }
    @After fun close() = database.close()

    @Test fun trimsUniqueLocationDateAndPreservesHistoryAfterCascadeDelete() = runBlocking<Unit> {
        val first = repository.saveDay(null, " 上海 ", "2026-10-19")
        val person = repository.savePerson(first, ShiftKind.NOON, null, draft())
        assertEquals(first, expectFailure<DuplicateDay> { repository.saveDay(null, "上海", "2026-10-19") }.existingId)
        assertEquals("上海", repository.days.first().single().day.locationName)
        val second = repository.saveDay(null, "北京", "2026-10-19")
        assertEquals(first, expectFailure<DuplicateDay> { repository.saveDay(second, " 上海 ", "2026-10-19") }.existingId)
        assertEquals("北京", database.dao().day(second)!!.day.locationName)
        repository.deleteDay(first)
        assertNull(database.dao().person(person))
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT * FROM OvertimeItem").use { it.count })
        assertEquals(setOf("上海", "北京"), repository.locations.first().map { it.name }.toSet())
        expectFailure<IllegalArgumentException> { repository.saveDay(null, " ", "2026-10-19") }
        expectFailure<IllegalArgumentException> { repository.saveDay(null, "广州", "2026-02-30") }
    }

    @Test fun warnsForSameNameButIgnoresLegacyPhoneAndAllowsConfirmedDuplicateAndCrossShift() = runBlocking {
        val day = repository.saveDay(null, "上海", "2026-10-19")
        val first = repository.savePerson(day, ShiftKind.NOON, null, draft("张三", "00123"))
        repository.savePerson(day, ShiftKind.NOON, first, draft("张三", "00123"))
        assertEquals(first, expectFailure<DuplicatePerson> { repository.savePerson(day, ShiftKind.NOON, null, draft("张三", "")) }.existingId)
        repository.savePerson(day, ShiftKind.NOON, null, draft("李四", "00123"))
        repository.savePerson(day, ShiftKind.NOON, null, draft("王五", ""))
        repository.savePerson(day, ShiftKind.NOON, null, draft("张三", "00123"), allowDuplicate = true)
        repository.savePerson(day, ShiftKind.EVENING, null, draft("张三", "00123"))
        assertEquals(5, database.dao().day(day)!!.people.size)
        repository.savePerson(day, ShiftKind.NOON, first, draft(" 张三 ", " 00123 "), allowDuplicate = true)
        assertEquals("00123", database.dao().person(first)!!.phone)
    }

    @Test fun inlineOvertimeKeepsStableIdsAndCountAndRollsBackInvalidOwnershipOrOverflow() = runBlocking<Unit> {
        val day = repository.saveDay(null, "旺辉", "2026-10-18")
        repository.setHeadFee(day, "5")
        val person = repository.savePerson(day, ShiftKind.NOON, null, draft().copy(overtime = emptyList()))
        val otherDay = repository.saveDay(null, "香樟", "2026-10-18")
        val other = repository.savePerson(otherDay, ShiftKind.NOON, null, draft().copy(overtime = emptyList()))
        val first = repository.saveOvertime(person, null, OvertimeDraft("加班1小时", "20.25"))
        val second = repository.saveOvertime(person, null, OvertimeDraft("收尾", "5"))
        assertEquals(first, repository.saveOvertime(person, first, OvertimeDraft("加班2小时", "22.25")))
        assertEquals(13225L, calculateDay(database.dao().day(day)!!).totalCents)
        expectFailure<IllegalArgumentException> { repository.saveOvertime(other, first, OvertimeDraft("错误归属", "1")) }
        expectFailure<IllegalArgumentException> { repository.deleteOvertime(other, first) }
        expectFailure<IllegalArgumentException> { repository.saveOvertime(person, null, OvertimeDraft(" ", "1")) }
        expectFailure<IllegalArgumentException> { repository.saveOvertime(person, first, OvertimeDraft("错误金额", "-1")) }
        expectFailure<ArithmeticException> { repository.saveOvertime(person, first, OvertimeDraft("过大", "92233720368547758.07")) }
        val saved = database.dao().day(day)!!.people.single()
        assertEquals(listOf(first, second), saved.overtime.sortedBy { it.sortOrder }.map { it.id })
        assertEquals("加班2小时", saved.overtime.first { it.id == first }.description)
        repository.deleteOvertime(person, second)
        val total = calculateDay(database.dao().day(day)!!)
        assertEquals(1, total.count)
        assertEquals(2225L, total.overtimeCents)
        assertEquals(12725L, total.totalCents)
        assertTrue(database.dao().day(otherDay)!!.people.single().overtime.isEmpty())
    }

    @Test fun failedEditAndOverflowPreserveSavedPersonAndOvertime() = runBlocking {
        val day = repository.saveDay(null, "上海", "2026-10-19")
        val id = repository.savePerson(day, ShiftKind.NOON, null, draft())
        expectFailure<IllegalArgumentException> {
            repository.savePerson(day, ShiftKind.NOON, id, draft().copy(name = "改名", overtime = listOf(OvertimeDraft("新的加班", "-1"))))
        }
        assertEquals("张三", database.dao().day(day)!!.people.single().person.name)
        assertEquals(4000L, database.dao().day(day)!!.people.single().overtime.single().amountCents)
        expectFailure<ArithmeticException> { repository.setHeadFee(day, "92233720368547758.07") }
        assertNull(database.dao().day(day)!!.day.headFeeCents)
        expectFailure<ArithmeticException> { repository.savePerson(day, ShiftKind.NOON, id, draft().copy(amount = "92233720368547758.07")) }
        assertEquals(10000L, database.dao().day(day)!!.people.single().person.baseAmountCents)
        repository.savePerson(day, ShiftKind.NOON, id, draft().copy(amount = "0", overtime = listOf(OvertimeDraft("新加班1", "0.1"), OvertimeDraft("新加班2", "0.2"))))
        assertEquals(listOf(10L, 20L), database.dao().day(day)!!.people.single().overtime.sortedBy { it.sortOrder }.map { it.amountCents })
        repository.setHeadFee(day, "0")
        assertEquals(30L, calculateDay(database.dao().day(day)!!).totalCents)
        repository.setHeadFee(day, " ")
        assertNull(calculateDay(database.dao().day(day)!!).totalCents)
        repository.deletePerson(id)
        assertTrue(database.dao().day(day)!!.people.isEmpty())
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT * FROM OvertimeItem").use { it.count })
    }

    @Test fun reopeningFileDatabaseRetainsSavedAmountsAndLocationHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fileName = "repository-test-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, AppDatabase::class.java, fileName).build()
        try {
            val original = AppRepository(disk)
            val id = original.saveDay(null, "持久化地点", "2026-10-19")
            original.setHeadFee(id, "0")
            original.savePerson(id, ShiftKind.NOON, null, draft())
            disk.close()
            disk = Room.databaseBuilder(context, AppDatabase::class.java, fileName).build()
            val reopened = AppRepository(disk)
            val saved = reopened.days.first().single()
            assertEquals(14000L, calculateDay(saved).totalCents)
            assertEquals("", saved.people.single().person.phone)
            reopened.deleteDay(id)
            assertEquals("持久化地点", reopened.locations.first().single().name)
        } finally {
            disk.close()
            context.deleteDatabase(fileName)
        }
    }

    private fun draft(name: String = "张三", phone: String = "") = PersonDraft(name, phone, "洗碗", "100", listOf(OvertimeDraft("加班2小时", "40")))
    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit): T {
        try { block() } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
