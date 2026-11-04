package sa.factory.rasedposposcashier.data.remote

import sa.factory.rasedposposcashier.model.RasedPosItem
import sa.factory.rasedposposcashier.model.RasedPosOrder

interface RasedPosApiService {
    suspend fun fetchCatalog(): List<RasedPosItem>
    suspend fun submitOrder(order: RasedPosOrder): Boolean
}
