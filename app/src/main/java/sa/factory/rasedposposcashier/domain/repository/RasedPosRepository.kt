package sa.factory.rasedposposcashier.domain.repository

import sa.factory.rasedposposcashier.model.RasedPosItem
import sa.factory.rasedposposcashier.model.RasedPosOrder
import sa.factory.rasedposposcashier.model.RasedPosTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RasedPosRepository {
    val catalogFlow: Flow<List<RasedPosItem>>
    val activeCart: StateFlow<List<RasedPosItem>>
    val telemetryFlow: Flow<RasedPosTelemetry>
    
    fun addToCart(item: RasedPosItem)
    fun removeFromCart(item: RasedPosItem)
    fun clearCart()
    suspend fun checkout(paymentMethod: String): RasedPosOrder
    suspend fun refreshCatalog()
}
