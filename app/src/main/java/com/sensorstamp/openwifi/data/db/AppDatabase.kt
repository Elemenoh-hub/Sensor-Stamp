package com.sensorstamp.openwifi.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NetworkEntity::class, SightingEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun networkDao(): NetworkDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "sensorstamp.db",
            )
                .addMigrations(Migrations.MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
