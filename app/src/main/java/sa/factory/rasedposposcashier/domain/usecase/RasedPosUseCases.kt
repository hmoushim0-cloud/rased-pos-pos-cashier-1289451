package sa.factory.rasedposposcashier.domain.usecase

import sa.factory.rasedposposcashier.domain.repository.RasedPosRepository
import sa.factory.rasedposposcashier.model.RasedPosItem
import sa.factory.rasedposposcashier.model.RasedPosOrder
import sa.factory.rasedposposcashier.model.RasedPosTelemetry
import kotlinx.coroutines.flow.Flow

class GetCatalogUseCase(private val repository: RasedPosRepository) {
    operator fun invoke(): Flow<List<RasedPosItem>> = repository.catalogFlow
}

class ManageCartUseCase(private val repository: RasedPosRepository) {
    val cart = repository.activeCart
    fun add(item: RasedPosItem) = repository.addToCart(item)
    fun remove(item: RasedPosItem) = repository.removeFromCart(item)
    fun clear() = repository.clearCart()
}

class ProcessCheckoutUseCase(private val repository: RasedPosRepository) {
    suspend operator fun invoke(paymentMethod: String = "Mada Contactless EMV"): RasedPosOrder =
        repository.checkout(paymentMethod)
}

class ObserveHardwareTelemetryUseCase(private val repository: RasedPosRepository) {
    operator fun invoke(): Flow<RasedPosTelemetry> = repository.telemetryFlow
}
