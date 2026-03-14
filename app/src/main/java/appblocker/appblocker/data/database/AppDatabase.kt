package appblocker.appblocker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import appblocker.appblocker.data.dao.BlockRuleDao
import appblocker.appblocker.data.entities.BlockRule

@Database(entities = [BlockRule::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockRuleDao(): BlockRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard.db"
                ).build().also { INSTANCE = it }
            }
    }
}
