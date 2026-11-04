package sa.factory.rasedposposcashier.domain.cashier

import sa.factory.rasedposposcashier.hardware.barcode.ScannedItemDetails
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.Locale

/**
 * Cashier Payment Tender Types accepted at Saudi POS
 */
enum class TenderMethod(val titleAr: String, val isElectronic: Boolean) {
    CASH("نقدا (ريال سعودي)", false),
    MADA_DEBIT("بطاقة مدى المصرفية الوطنية", true),
    APPLE_PAY("الدفع عبر أبل باي (NFC)", true),
    CREDIT_CARD("بطاقة ائتمانية (Visa / MC)", true),
    LOYALTY_POINTS("استبدال نقاط الولاء (مكافأة / قطاف)", true)
}

/**
 * Multi-Tender Split Payment Allocation
 */
data class TenderPaymentSplit(
    val method: TenderMethod,
    val amountSar: Double,
    val referenceNumber: String? = null
) : Serializable

/**
 * Cashier Shift Settlement Report Type
 */
enum class ShiftReportType(val titleAr: String) {
    X_REPORT_INTERIM("تقرير القراءة الوسطية (X-Report) - بدون إغلاق الصندوق"),
    Z_REPORT_END_OF_DAY("تقرير التسوية النهائي (Z-Report) - إغلاق الصندوق والترحيل")
}

/**
 * Standard ESC/POS Command Generator for Hardware Peripherals
 */
object EscPosCommandBuilder {
    // Standard ESC/POS Cash Drawer pulse command: ESC p <pin> <t1> <t2>
    // 0x1B 0x70 0x00 0x19 0xFA sends a 50ms ON pulse to Drawer Pin 2
    val KICK_CASH_DRAWER_PIN2 = byteArrayOf(0x1B.toByte(), 0x70.toByte(), 0x00.toByte(), 0x19.toByte(), 0xFA.toByte())
    val KICK_CASH_DRAWER_PIN5 = byteArrayOf(0x1B.toByte(), 0x70.toByte(), 0x01.toByte(), 0x19.toByte(), 0xFA.toByte())
    val PAPER_FULL_CUT = byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x00.toByte())
    val PAPER_PARTIAL_CUT = byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x01.toByte())
}

/**
 * POS Sale Transaction Model with Multi-Tender Support
 */
data class PosSaleTransaction(
    val receiptId: String,
    val items: List<ScannedItemDetails>,
    val subtotalSar: Double,
    val vatSar: Double, // 15% ZATCA VAT
    val totalSar: Double,
    val payments: List<TenderPaymentSplit> = emptyList(),
    val totalPaidSar: Double = 0.0,
    val cashTenderedSar: Double = 0.0,
    val changeDueSar: Double = 0.0,
    val isFullySettled: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * Cashier Shift Reconciliation (X/Z Report)
 */
data class CashierShiftSession(
    val shiftId: String,
    val cashierId: String,
    val cashierNameAr: String,
    val openingFloatSar: Double,
    val grossSalesSar: Double = 0.0,
    val totalZatcaVatSar: Double = 0.0,
    val salesByTender: Map<TenderMethod, Double> = emptyMap(),
    val transactionCount: Int = 0,
    val cashDropsSar: Double = 0.0,
    val expectedCashInDrawerSar: Double = 0.0,
    val countedCashInDrawerSar: Double? = null,
    val cashVarianceSar: Double? = null, // Over (+) or Short (-)
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val isClosed: Boolean = false
) : Serializable

/**
 * Autonomous Multi-Tender Cashier Settlement & Shift Reconciliation Engine
 */
class CashierTenderSettlementEngine {

    companion object {
        const val ZATCA_VAT_RATE = 0.15 // 15% Standard KSA VAT
    }

    /**
     * Initializes a sale transaction from scanned item list
     */
    fun createSaleTransaction(receiptId: String, items: List<ScannedItemDetails>): PosSaleTransaction {
        val subtotal = Math.round(items.sumByDouble { it.lineTotalSar } * 100.0) / 100.0
        val vat = Math.round(subtotal * ZATCA_VAT_RATE * 100.0) / 100.0
        val total = Math.round((subtotal + vat) * 100.0) / 100.0

        return PosSaleTransaction(
            receiptId = receiptId,
            items = items,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total
        )
    }

    /**
     * Applies multi-tender split payments and computes cash change due
     */
    fun applySplitTenderPayments(
        transaction: PosSaleTransaction,
        splits: List<TenderPaymentSplit>,
        cashTenderedSar: Double = 0.0
    ): PosSaleTransaction {
        val totalPaid = Math.round(splits.sumByDouble { it.amountSar } * 100.0) / 100.0
        require(totalPaid >= transaction.totalSar - 0.01) {
            "Total payments (SAR $totalPaid) insufficient for bill total (SAR ${transaction.totalSar})"
        }

        val cashPortion = splits.firstOrNull { it.method == TenderMethod.CASH }?.amountSar ?: 0.0
        val changeDue = if (cashPortion > 0.0 && cashTenderedSar >= cashPortion) {
            Math.round((cashTenderedSar - cashPortion) * 100.0) / 100.0
        } else {
            0.0
        }

        return transaction.copy(
            payments = splits,
            totalPaidSar = totalPaid,
            cashTenderedSar = cashTenderedSar,
            changeDueSar = changeDue,
            isFullySettled = true,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Generates ESC/POS pulse frame to kick open cash drawer
     */
    fun buildCashDrawerKickFrame(pin2: Boolean = true): ByteArray {
        return if (pin2) EscPosCommandBuilder.KICK_CASH_DRAWER_PIN2 else EscPosCommandBuilder.KICK_CASH_DRAWER_PIN5
    }

    /**
     * Opens a new Cashier Shift with initial float
     */
    fun openShift(
        shiftId: String,
        cashierId: String,
        cashierNameAr: String,
        openingFloatSar: Double
    ): CashierShiftSession {
        return CashierShiftSession(
            shiftId = shiftId,
            cashierId = cashierId,
            cashierNameAr = cashierNameAr,
            openingFloatSar = openingFloatSar,
            expectedCashInDrawerSar = openingFloatSar,
            openedAt = System.currentTimeMillis()
        )
    }

    /**
     * Accumulates settled sale transaction into active shift ledger
     */
    fun recordTransaction(
        session: CashierShiftSession,
        transaction: PosSaleTransaction
    ): CashierShiftSession {
        require(!session.isClosed) { "Cannot record transactions to a closed cashier shift" }
        require(transaction.isFullySettled) { "Transaction must be fully settled before ledger recording" }

        val newGross = Math.round((session.grossSalesSar + transaction.totalSar) * 100.0) / 100.0
        val newVat = Math.round((session.totalZatcaVatSar + transaction.vatSar) * 100.0) / 100.0

        val tenderMap = session.salesByTender.toMutableMap()
        var cashSalesDelta = 0.0

        for (split in transaction.payments) {
            val currentTenderTotal = tenderMap.getOrElse(split.method) { 0.0 }
            tenderMap[split.method] = Math.round((currentTenderTotal + split.amountSar) * 100.0) / 100.0
            if (split.method == TenderMethod.CASH) {
                cashSalesDelta += split.amountSar
            }
        }

        val expectedCash = Math.round((session.expectedCashInDrawerSar + cashSalesDelta) * 100.0) / 100.0

        return session.copy(
            grossSalesSar = newGross,
            totalZatcaVatSar = newVat,
            salesByTender = tenderMap,
            transactionCount = session.transactionCount + 1,
            expectedCashInDrawerSar = expectedCash
        )
    }

    /**
     * Performs End-of-Day Z-Report shift closure and computes cash variance
     */
    fun closeShiftZReport(
        session: CashierShiftSession,
        countedCashInDrawerSar: Double,
        cashDropsSar: Double = 0.0
    ): CashierShiftSession {
        require(!session.isClosed) { "Cashier shift is already closed" }

        val netExpectedCash = Math.round((session.expectedCashInDrawerSar - cashDropsSar) * 100.0) / 100.0
        val variance = Math.round((countedCashInDrawerSar - netExpectedCash) * 100.0) / 100.0

        return session.copy(
            cashDropsSar = cashDropsSar,
            expectedCashInDrawerSar = netExpectedCash,
            countedCashInDrawerSar = countedCashInDrawerSar,
            cashVarianceSar = variance,
            isClosed = true,
            closedAt = System.currentTimeMillis()
        )
    }
}
