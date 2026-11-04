package sa.factory.rasedposposcashier.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import sa.factory.rasedposposcashier.model.RasedPosItem

@Entity(tableName = "pos_cashier_items_cache")
data class RasedPosEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val priceSar: Double,
    val badge: String,
    val category: String,
    val status: String
) {
    fun toDomain(): RasedPosItem = RasedPosItem(
        id = id,
        title = title,
        description = description,
        priceSar = priceSar,
        badge = badge,
        category = category,
        status = status
    )
}
