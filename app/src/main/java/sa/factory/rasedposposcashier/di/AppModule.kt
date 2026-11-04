package sa.factory.rasedposposcashier.di

import sa.factory.rasedposposcashier.data.repository.RasedPosRepositoryImpl
import sa.factory.rasedposposcashier.domain.repository.RasedPosRepository
import sa.factory.rasedposposcashier.domain.usecase.*
import sa.factory.rasedposposcashier.hardware.HardwareSensorDriver
import sa.factory.rasedposposcashier.ui.viewmodel.RasedPosViewModel

class DiContainer {
    val sensorDriver = HardwareSensorDriver()
    val repository: RasedPosRepository = RasedPosRepositoryImpl(sensorDriver)

    val getCatalogUseCase = GetCatalogUseCase(repository)
    val manageCartUseCase = ManageCartUseCase(repository)
    val processCheckoutUseCase = ProcessCheckoutUseCase(repository)
    val observeHardwareTelemetryUseCase = ObserveHardwareTelemetryUseCase(repository)

    fun provideViewModel(): RasedPosViewModel = RasedPosViewModel(
        getCatalogUseCase,
        manageCartUseCase,
        processCheckoutUseCase,
        observeHardwareTelemetryUseCase
    )
}
