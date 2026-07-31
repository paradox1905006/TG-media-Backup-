package com.dparadox.tgbackup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UploadedFile::class, SelectedMedia::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun uploadedFileDao(): UploadedFileDao
    abstract fun selectedMediaDao(): SelectedMediaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop old excluded_media and create selected_media
                db.execSQL("DROP TABLE IF EXISTS `excluded_media`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `selected_media` (`hash` TEXT NOT NULL, PRIMARY KEY(`hash`))")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tg_backup_db"
                )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
