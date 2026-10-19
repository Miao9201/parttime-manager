package cn.miao.parttime

import android.app.Application
import androidx.room.Room
import cn.miao.parttime.data.AppDatabase
import cn.miao.parttime.data.AppRepository

class PartTimeApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "parttime-manager.db").build()
    }
    val repository by lazy { AppRepository(database) }
}
