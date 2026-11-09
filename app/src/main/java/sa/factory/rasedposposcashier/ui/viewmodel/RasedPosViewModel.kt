package sa.factory.rasedposposcashier.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import sa.factory.rasedposposcashier.domain.usecase.*
import sa.factory.rasedposposcashier.model.RasedPosItem
import sa.factory.rasedposposcashier.model.RasedPosOrder
import sa.factory.rasedposposcashier.model.RasedPosTelemetry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RasedPosUiState(
    val catalog: List<RasedPosItem> = emptyList(),
    val filteredCatalog: List<RasedPosItem> = emptyList(),
    val selectedCategory: String = "الكل",
    val cart: List<RasedPosItem> = emptyList(),
    val telemetry: RasedPosTelemetry? = null,
    val lastOrder: RasedPosOrder? = null,
    val isCheckingOut: Boolean = false,
    val activeTab: String = "home"
)

class RasedPosViewModel(
    private val getCatalogUseCase: GetCatalogUseCase,
    private val manageCartUseCase: ManageCartUseCase,
    private val processCheckoutUseCase: ProcessCheckoutUseCase,
    private val observeHardwareTelemetryUseCase: ObserveHardwareTelemetryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RasedPosUiState())
    val uiState: StateFlow<RasedPosUiState> = _uiState.asStateFlow()

    init {
        // Observe Catalog
        getCatalogUseCase().onEach { list ->
            _uiState.update { it.copy(catalog = list, filteredCatalog = list) }
        }.launchIn(viewModelScope)

        // Observe Cart
        manageCartUseCase.cart.onEach { cartItems ->
            _uiState.update { it.copy(cart = cartItems) }
        }.launchIn(viewModelScope)

        // Observe Telemetry
        observeHardwareTelemetryUseCase().onEach { telem ->
            _uiState.update { it.copy(telemetry = telem) }
        }.launchIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val filtered = if (category == "الكل") state.catalog else state.catalog.filter { it.category == category }
            state.copy(selectedCategory = category, filteredCatalog = filtered)
        }
    }

    fun switchTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun addToCart(item: RasedPosItem) = manageCartUseCase.add(item)
    fun removeFromCart(item: RasedPosItem) = manageCartUseCase.remove(item)

    fun checkout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingOut = true) }
            val order = processCheckoutUseCase()
            _uiState.update { it.copy(isCheckingOut = false, lastOrder = order, activeTab = "checkout") }
        }
    }
}
