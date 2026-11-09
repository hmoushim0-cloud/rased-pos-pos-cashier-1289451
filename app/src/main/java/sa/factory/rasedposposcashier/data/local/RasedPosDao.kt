package sa.factory.rasedposposcashier.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RasedPosDao {
    @Query("SELECT * FROM pos_cashier_items_cache ORDER BY id ASC")
    fun observeAllItems(): Flow<List<RasedPosEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RasedPosEntity>)

    @Query("DELETE FROM pos_cashier_items_cache")
    suspend fun clearAll()
}
