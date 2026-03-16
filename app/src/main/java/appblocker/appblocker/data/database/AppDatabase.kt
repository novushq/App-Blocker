package appblocker.appblocker.data.database

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import appblocker.appblocker.data.dao.BlockRuleDao
import appblocker.appblocker.data.entities.BlockRule

@Database(entities = [BlockRule::class], version = 2, exportSchema = false)
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE block_rules ADD COLUMN pausedUntilMs INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
