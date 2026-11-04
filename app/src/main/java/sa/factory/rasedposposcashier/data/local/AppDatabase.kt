package sa.factory.rasedposposcashier.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RasedPosEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pos_cashierDao(): RasedPosDao
}
