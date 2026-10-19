package cn.miao.parttime.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Transaction
    @Query("SELECT * FROM DayRecord ORDER BY localDate DESC, updatedAt DESC, id DESC")
    fun observeDays(): Flow<List<DayBundle>>

    @Query("SELECT * FROM SavedLocation ORDER BY lastUsedAt DESC, nameKey ASC")
    fun observeLocations(): Flow<List<SavedLocation>>

    @Transaction
    @Query("SELECT * FROM DayRecord WHERE id = :id")
    suspend fun day(id: Long): DayBundle?

    @Query("SELECT id FROM DayRecord WHERE locationKey = :locationKey AND localDate = :date LIMIT 1")
    suspend fun findDay(locationKey: String, date: String): Long?

    @Insert suspend fun insertDay(day: DayRecord): Long
    @Update suspend fun updateDay(day: DayRecord)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLocation(location: SavedLocation)
    @Query("DELETE FROM DayRecord WHERE id = :id") suspend fun deleteDay(id: Long)
    @Insert suspend fun insertPerson(person: Assignment): Long
    @Update suspend fun updatePerson(person: Assignment)
    @Query("SELECT * FROM Assignment WHERE id = :id") suspend fun person(id: Long): Assignment?
    @Query("DELETE FROM Assignment WHERE id = :id") suspend fun deletePerson(id: Long)
    @Insert suspend fun insertOvertime(items: List<OvertimeItem>)
    @Insert suspend fun insertOvertimeItem(item: OvertimeItem): Long
    @Update suspend fun updateOvertimeItem(item: OvertimeItem)
    @Query("SELECT * FROM OvertimeItem WHERE id = :id") suspend fun overtimeItem(id: Long): OvertimeItem?
    @Query("DELETE FROM OvertimeItem WHERE id = :id") suspend fun deleteOvertimeItem(id: Long)
    @Query("DELETE FROM OvertimeItem WHERE assignmentId = :id") suspend fun deleteOvertime(id: Long)
}

@Database(entities = [DayRecord::class, SavedLocation::class, Assignment::class, OvertimeItem::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}
