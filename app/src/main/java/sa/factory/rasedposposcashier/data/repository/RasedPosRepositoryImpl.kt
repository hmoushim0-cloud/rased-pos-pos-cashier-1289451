package sa.factory.rasedposposcashier.data.repository

import sa.factory.rasedposposcashier.domain.repository.RasedPosRepository
import sa.factory.rasedposposcashier.hardware.HardwareSensorDriver
import sa.factory.rasedposposcashier.model.RasedPosItem
import sa.factory.rasedposposcashier.model.RasedPosOrder
import sa.factory.rasedposposcashier.model.RasedPosTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RasedPosRepositoryImpl(
    private val sensorDriver: HardwareSensorDriver
) : RasedPosRepository {

    private val _catalog = MutableStateFlow<List<RasedPosItem>>(listOf(
        RasedPosItem("1", "الباقة التنفيذية المعتمدة", "تكامل مباشر مع ESC/POS Thermal Receipt Printer + Barcode Scanner", 180.0, "معتمد", "الخدمات الرئيسية"),
        RasedPosItem("2", "خدمة المعالجة الفورية", "أداء أصيل بسرعة 60 إطار في الثانية", 95.0, "فوري", "المراقبة الحية"),
        RasedPosItem("3", "باقة الصيانة والدعم السحابي", "تشفير كامل للبيانات المحلية ومزامنة آمنة", 250.0, "شامل", "التقارير الضريبية")
    ))
    override val catalogFlow: Flow<List<RasedPosItem>> = _catalog.asStateFlow()

    private val _cart = MutableStateFlow<List<RasedPosItem>>(emptyList())
    override val activeCart: StateFlow<List<RasedPosItem>> = _cart.asStateFlow()

    override val telemetryFlow: Flow<RasedPosTelemetry> = sensorDriver.observePeripheralStream()
        .map { frame ->
            RasedPosTelemetry(
                peripheralName = frame.peripheralName,
                status = frame.connectionState,
                liveReading = frame.livePayload,
                batteryPct = 96,
                signalDbm = -54
            )
        }

    override fun addToCart(item: RasedPosItem) {
        val current = _cart.value.toMutableList()
        current.add(item)
        _cart.value = current
    }

    override fun removeFromCart(item: RasedPosItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        _cart.value = current
    }

    override fun clearCart() {
        _cart.value = emptyList()
    }

    override suspend fun checkout(paymentMethod: String): RasedPosOrder {
        val currentItems = _cart.value.toList()
        val subtotal = currentItems.sumOf { it.priceSar }
        val vat = subtotal * 0.15
        val total = subtotal + vat
        val order = RasedPosOrder(
            orderId = "ORD-${System.currentTimeMillis() % 100000}",
            items = currentItems,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            paymentMethod = paymentMethod
        )
        clearCart()
        return order
    }

    override suspend fun refreshCatalog() {
        // Offline-first sync logic
    }
}
