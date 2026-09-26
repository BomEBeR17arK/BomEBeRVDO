package com.bomeber.homestream.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DownloadEntity::class, DownloadUnitEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DownloadConverters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var INSTANCE: DownloadDatabase? = null

        fun get(context: Context): DownloadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "homestream_downloads.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN selectedVariantUrl TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN selectedQualityLabel TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN selectedWidth INTEGER")
                db.execSQL("ALTER TABLE downloads ADD COLUMN selectedHeight INTEGER")
                db.execSQL("ALTER TABLE downloads ADD COLUMN selectedBandwidth INTEGER")
            }
        }
    }
}
