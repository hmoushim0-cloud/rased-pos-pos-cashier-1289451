package sa.factory.rasedposposcashier

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.factory.rasedposposcashier.domain.cashier.*
import sa.factory.rasedposposcashier.hardware.barcode.*

class RasedPosCashierFeatureTest {

    private lateinit var barcodeEngine: Gs1BarcodeScannerEngine
    private lateinit var settlementEngine: CashierTenderSettlementEngine

    @Before
    fun setUp() {
        barcodeEngine = Gs1BarcodeScannerEngine()
        settlementEngine = CashierTenderSettlementEngine()
    }

    @Test
    fun testEan13Modulo10CheckDigitCalculationAndValidation() {
        // "628100100012" -> sum: (6+8+0+1+0+0)*1 + (2+1+0+0+0+1)*3 = 15 + 12 = 27 -> mod 10 = 7 -> 10-7 = 3? Wait!
        // Let's compute actual check digit:
        val twelveDigits = "628100100012"
        val calculatedCheck = Ean13CheckDigit.calculate(twelveDigits)
        val validEan13 = twelveDigits + calculatedCheck

        assertTrue("Generated EAN-13 must be valid", Ean13CheckDigit.isValid(validEan13))

        // Corrupt check digit
        val corruptedCheck = (calculatedCheck + 1) % 10
        val invalidEan13 = twelveDigits + corruptedCheck
        assertFalse("Corrupted check digit must fail verification", Ean13CheckDigit.isValid(invalidEan13))
    }

    @Test
    fun testGs1128BracketedAiParser() {
        val gs1Barcode = "(01)06281001000123(17)261231(10)LOT8821(3102)001550"
        val parsedList = barcodeEngine.parseGs1128(gs1Barcode)

        assertEquals("Must parse 4 Application Identifiers", 4, parsedList.size)

        val gtin = parsedList.firstOrNull { it.ai == "01" }
        assertNotNull(gtin)
        assertEquals("06281001000123", gtin?.value)

        val expiry = parsedList.firstOrNull { it.ai == "17" }
        assertNotNull(expiry)
        assertEquals("261231", expiry?.value)

        val lot = parsedList.firstOrNull { it.ai == "10" }
        assertNotNull(lot)
        assertEquals("LOT8821", lot?.value)

        val weight = parsedList.firstOrNull { it.ai == "3102" }
        assertNotNull(weight)
        assertEquals("001550", weight?.value)
    }

    @Test
    fun testSaudiVariableWeightScaleBarcodeParsing() {
        // Construct valid 13-digit scale barcode: prefix "21", PLU "1045", separator "0", weight "1450" (1.450 kg)
        val twelve = "21104501450" // 11 digits? Wait, 2 (PP) + 4 (IIII) + 1 (C) + 4 (WWWW) = 11 digits? Let's check length:
        // PP(2) + IIII(4) + C(1) + WWWW(4) = 11 digits, but EAN-13 needs 12 + check = 13!
        // Format: PP(2) + IIII(4) + C(1) + WWWWW(5)? Or PP(2) + IIII(4) + WWWW(5) + K(1)?
        // In Gs1BarcodeScannerEngine: plu is substring(2,6) [4 digits], grams is substring(7,11) [4 digits]
        // substring(0,2) is PP [2 digits], substring(2,6) is PLU [4 digits], substring(6,7) is C [1 digit], substring(7,11) is WWWW [4 digits], substring(11,12) is 1 digit, substring(12,13) is check!
        // Total = 2 + 4 + 1 + 4 + 1 = 12 digits + 1 check digit = 13 digits!
        val prefixAndPluAndWeight = "211045014500" // 12 digits
        val checkDigit = Ean13CheckDigit.calculate(prefixAndPluAndWeight)
        val fullBarcode = prefixAndPluAndWeight + checkDigit

        val scanned = barcodeEngine.parseVariableWeightScaleBarcode(fullBarcode) { plu ->
            if (plu == "1045") Pair("تمر سكري فاخر القصيم", 24.00) else Pair("منتج غير معروف", 10.00)
        }

        assertEquals(BarcodeSymbology.VARIABLE_WEIGHT_IN_STORE, scanned.symbology)
        assertEquals("1045", scanned.itemPlu)
        assertEquals("تمر سكري فاخر القصيم", scanned.nameAr)
        assertEquals(24.00, scanned.unitPriceSar, 0.001)
        assertEquals(1.450, scanned.quantityOrWeightKg, 0.001)
        assertTrue(scanned.isWeighted)
        // 1.450 kg * 24.00 SAR = 34.80 SAR
        assertEquals(34.80, scanned.lineTotalSar, 0.01)
    }

    @Test
    fun testEscPosCashDrawerKickerCommand() {
        val kickPin2 = settlementEngine.buildCashDrawerKickFrame(pin2 = true)
        assertEquals(5, kickPin2.size)
        assertEquals(0x1B.toByte(), kickPin2[0]) // ESC
        assertEquals(0x70.toByte(), kickPin2[1]) // 'p'
        assertEquals(0x00.toByte(), kickPin2[2]) // Pin 2
        assertEquals(0x19.toByte(), kickPin2[3]) // t1 = 25 (50ms)
        assertEquals(0xFA.toByte(), kickPin2[4]) // t2 = 250 (500ms)

        val kickPin5 = settlementEngine.buildCashDrawerKickFrame(pin2 = false)
        assertEquals(0x01.toByte(), kickPin5[2]) // Pin 5
    }

    @Test
    fun testMultiTenderSplitPaymentAndChangeCalculation() {
        val items = listOf(
            ScannedItemDetails("6281001", BarcodeSymbology.EAN_13, "6281001", "زيت زيتون الجوف", 100.0, 1.0, false, 100.0),
            ScannedItemDetails("2110450", BarcodeSymbology.VARIABLE_WEIGHT_IN_STORE, "1045", "تمر سكري القصيم", 24.0, 1.45, true, 34.80)
        )

        val sale = settlementEngine.createSaleTransaction("REC-2026-9901", items)
        // Subtotal = 100.0 + 34.80 = 134.80 SAR
        assertEquals(134.80, sale.subtotalSar, 0.01)
        // ZATCA VAT 15% = 134.80 * 0.15 = 20.22 SAR
        assertEquals(20.22, sale.vatSar, 0.01)
        // Total = 155.02 SAR
        assertEquals(155.02, sale.totalSar, 0.01)

        // Split Tender Payment:
        // 1. Mada Card: 100.00 SAR
        // 2. Cash: 55.02 SAR (Customer hands a 100 SAR bill)
        val splits = listOf(
            TenderPaymentSplit(TenderMethod.MADA_DEBIT, 100.00, "AUTH-MD-8819"),
            TenderPaymentSplit(TenderMethod.CASH, 55.02)
        )

        val settledSale = settlementEngine.applySplitTenderPayments(sale, splits, cashTenderedSar = 100.00)
        assertTrue(settledSale.isFullySettled)
        assertEquals(155.02, settledSale.totalPaidSar, 0.01)
        // Change due = 100.00 - 55.02 = 44.98 SAR
        assertEquals(44.98, settledSale.changeDueSar, 0.01)
    }

    @Test
    fun testCashierShiftReconciliationAndVariance() {
        val shift = settlementEngine.openShift("SHIFT-01", "CASHIER-42", "أحمد الشمري", openingFloatSar = 500.00)
        assertEquals(500.00, shift.expectedCashInDrawerSar, 0.01)

        val items = listOf(
            ScannedItemDetails("EAN1", BarcodeSymbology.EAN_13, "EAN1", "عسل سدر بلدي", 134.80, 1.0, false, 134.80)
        )
        val sale = settlementEngine.createSaleTransaction("REC-01", items)
        val splits = listOf(
            TenderPaymentSplit(TenderMethod.MADA_DEBIT, 100.00),
            TenderPaymentSplit(TenderMethod.CASH, 55.02)
        )
        val settled = settlementEngine.applySplitTenderPayments(sale, splits, 55.02)

        val updatedShift = settlementEngine.recordTransaction(shift, settled)
        assertEquals(1, updatedShift.transactionCount)
        assertEquals(155.02, updatedShift.grossSalesSar, 0.01)
        // Expected cash = 500.00 (float) + 55.02 (cash sale) = 555.02 SAR
        assertEquals(555.02, updatedShift.expectedCashInDrawerSar, 0.01)

        // Close shift: Cashier counted 555.00 SAR (2 halalas shortage)
        val closedShift = settlementEngine.closeShiftZReport(updatedShift, countedCashInDrawerSar = 555.00)
        assertTrue(closedShift.isClosed)
        assertNotNull(closedShift.closedAt)
        // Variance = 555.00 - 555.02 = -0.02 SAR
        assertEquals(-0.02, closedShift.cashVarianceSar ?: 0.0, 0.001)
    }
}
