package com.dparadox.tgbackup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dparadox.tgbackup.gallery.db.dao.AlbumDao
import com.dparadox.tgbackup.gallery.db.dao.MediaItemDao
import com.dparadox.tgbackup.gallery.db.entity.AlbumEntity
import com.dparadox.tgbackup.gallery.db.entity.MediaItemEntity

@Database(
    entities = [
        UploadedFile::class,
        SelectedMedia::class,
        MediaItemEntity::class,
        AlbumEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun uploadedFileDao(): UploadedFileDao
    abstract fun selectedMediaDao(): SelectedMediaDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun albumDao(): AlbumDao

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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create gallery tables
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gallery_media_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mediaStoreId` INTEGER NOT NULL,
                        `uri` TEXT NOT NULL,
                        `filePath` TEXT,
                        `displayName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `dateTaken` INTEGER NOT NULL,
                        `dateAdded` INTEGER NOT NULL,
                        `dateModified` INTEGER NOT NULL,
                        `bucketId` TEXT NOT NULL,
                        `bucketName` TEXT NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `durationMs` INTEGER,
                        `isFavorite` INTEGER NOT NULL DEFAULT 0,
                        `isHidden` INTEGER NOT NULL DEFAULT 0,
                        `isTrashed` INTEGER NOT NULL DEFAULT 0,
                        `trashedAt` INTEGER,
                        `deletedAt` INTEGER
                    )
                    """
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_gallery_media_items_mediaStoreId` " +
                    "ON `gallery_media_items` (`mediaStoreId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gallery_media_items_bucketId` " +
                    "ON `gallery_media_items` (`bucketId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gallery_media_items_isHidden` " +
                    "ON `gallery_media_items` (`isHidden`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gallery_media_items_isTrashed` " +
                    "ON `gallery_media_items` (`isTrashed`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gallery_media_items_trashedAt` " +
                    "ON `gallery_media_items` (`trashedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gallery_media_items_dateTaken` " +
                    "ON `gallery_media_items` (`dateTaken`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gallery_albums` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bucketId` TEXT NOT NULL,
                        `bucketName` TEXT NOT NULL,
                        `coverUri` TEXT,
                        `itemCount` INTEGER NOT NULL DEFAULT 0,
                        `dateModified` INTEGER NOT NULL DEFAULT 0
                    )
                    """
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_gallery_albums_bucketId` " +
                    "ON `gallery_albums` (`bucketId`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tg_backup_db"
                )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}