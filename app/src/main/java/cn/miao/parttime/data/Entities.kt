package cn.miao.parttime.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class ShiftKind(val label: String) { NOON("中午"), EVENING("晚上") }

@Entity(indices = [Index(value = ["locationKey", "localDate"], unique = true)])
data class DayRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationName: String,
    val locationKey: String,
    val localDate: String,
    val headFeeCents: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Retain the legacy column so existing databases open without changing saved records.
    val isDemo: Boolean = false,
)

@Entity
data class SavedLocation(
    @PrimaryKey val nameKey: String,
    val name: String,
    val lastUsedAt: Long,
)

@Entity(
    foreignKeys = [ForeignKey(entity = DayRecord::class, parentColumns = ["id"], childColumns = ["dayId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dayId")],
)
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val shiftKind: String,
    val name: String,
    val phone: String,
    val task: String,
    val baseAmountCents: Long,
    val sortOrder: Int,
)

@Entity(
    foreignKeys = [ForeignKey(entity = Assignment::class, parentColumns = ["id"], childColumns = ["assignmentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("assignmentId")],
)
data class OvertimeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assignmentId: Long,
    val description: String,
    val amountCents: Long,
    val sortOrder: Int,
)

data class PersonBundle(
    @Embedded val person: Assignment,
    @Relation(parentColumn = "id", entityColumn = "assignmentId") val overtime: List<OvertimeItem>,
)

data class DayBundle(
    @Embedded val day: DayRecord,
    @Relation(entity = Assignment::class, parentColumn = "id", entityColumn = "dayId") val people: List<PersonBundle>,
)

data class OvertimeDraft(val description: String = "", val amount: String = "")
data class PersonDraft(
    val name: String = "",
    val phone: String = "",
    val task: String = "",
    val amount: String = "",
    val overtime: List<OvertimeDraft> = emptyList(),
)

class DuplicateDay(val existingId: Long) : IllegalArgumentException("此地点在这一天已有记录")
class DuplicatePerson(val existingId: Long) : IllegalArgumentException("本班已有相同姓名，请确认是否继续添加")
