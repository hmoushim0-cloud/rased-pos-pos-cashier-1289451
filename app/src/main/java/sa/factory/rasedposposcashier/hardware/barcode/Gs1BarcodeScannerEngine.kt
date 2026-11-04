package sa.factory.rasedposposcashier.hardware.barcode

import java.io.Serializable
import java.util.Locale

/**
 * Standard Barcode Symbologies encountered at Saudi Cashier Checkouts
 */
enum class BarcodeSymbology(val title: String) {
    EAN_13("EAN-13 Standard Global Retail Barcode"),
    UPC_A("UPC-A North American Format"),
    GS1_128("GS1-128 Supply Chain Logistics Barcode"),
    VARIABLE_WEIGHT_IN_STORE("Saudi In-Store Variable Weight / Price Barcode (Prefix 20-29)"),
    CODE_128("Code-128 General Alpha-Numeric Barcode")
}

/**
 * Parsed GS1 Application Identifier (AI) Data Element
 */
data class Gs1AiElement(
    val ai: String,
    val title: String,
    val value: String
) : Serializable

/**
 * High-Level Scanned POS Line Item Data
 */
data class ScannedItemDetails(
    val rawBarcode: String,
    val symbology: BarcodeSymbology,
    val itemPlu: String,
    val nameAr: String,
    val unitPriceSar: Double,
    val quantityOrWeightKg: Double,
    val isWeighted: Boolean,
    val lineTotalSar: Double,
    val batchNumber: String? = null,
    val expirationDateFormatted: String? = null
) : Serializable

/**
 * EAN-13 Modulo-10 Check Digit Calculator
 */
object Ean13CheckDigit {

    fun calculate(twelveDigits: String): Int {
        require(twelveDigits.length == 12 && twelveDigits.all { it.isDigit() }) {
            "Input must be exactly 12 numeric digits"
        }

        var sum = 0
        for (i in 0 until 12) {
            val digit = twelveDigits[i].toInt() - '0'.toInt()
            sum += if (i % 2 == 0) digit * 1 else digit * 3
        }

        val mod = sum % 10
        return if (mod == 0) 0 else 10 - mod
    }

    fun isValid(thirteenDigits: String): Boolean {
        if (thirteenDigits.length != 13 || !thirteenDigits.all { it.isDigit() }) return false
        val expectedCheck = calculate(thirteenDigits.substring(0, 12))
        val actualCheck = thirteenDigits[12].toInt() - '0'.toInt()
        return expectedCheck == actualCheck
    }
}

/**
 * GS1-128 & Variable-Weight Barcode Scanner Engine
 */
class Gs1BarcodeScannerEngine {

    companion object {
        // In-store prefix range for supermarket scales (Saudi SASO / GS1 standard)
        val IN_STORE_VARIABLE_PREFIXES = setOf("20", "21", "22", "23", "24", "25", "26", "27", "28", "29")
    }

    /**
     * Identifies barcode symbology from raw scanned character stream
     */
    fun identifySymbology(rawBarcode: String): BarcodeSymbology {
        val clean = rawBarcode.trim()
        return when {
            clean.startsWith("(") && clean.contains(")") -> BarcodeSymbology.GS1_128
            clean.length == 13 && clean.all { it.isDigit() } &&
                    IN_STORE_VARIABLE_PREFIXES.contains(clean.substring(0, 2)) -> BarcodeSymbology.VARIABLE_WEIGHT_IN_STORE
            clean.length == 13 && clean.all { it.isDigit() } -> BarcodeSymbology.EAN_13
            clean.length == 12 && clean.all { it.isDigit() } -> BarcodeSymbology.UPC_A
            else -> BarcodeSymbology.CODE_128
        }
    }

    /**
     * Parses GS1-128 barcode containing bracketed Application Identifiers
     * Example: "(01)06281001000123(17)261231(10)LOT8821(3102)001550"
     */
    fun parseGs1128(rawBarcode: String): List<Gs1AiElement> {
        val elements = mutableListOf<Gs1AiElement>()
        var remaining = rawBarcode.trim()

        while (remaining.startsWith("(") && remaining.contains(")")) {
            val closeIndex = remaining.indexOf(')')
            val ai = remaining.substring(1, closeIndex)
            val afterAi = remaining.substring(closeIndex + 1)

            val nextOpen = afterAi.indexOf('(')
            val value = if (nextOpen != -1) {
                afterAi.substring(0, nextOpen)
            } else {
                afterAi
            }

            val title = when (ai) {
                "01" -> "Global Trade Item Number (GTIN-14)"
                "10" -> "Batch or Lot Number"
                "17" -> "Expiration Date (YYMMDD)"
                "21" -> "Serial Number"
                "3102" -> "Net Weight in Kilograms (2 Decimals)"
                else -> "Application Identifier $ai"
            }

            elements.add(Gs1AiElement(ai, title, value))
            remaining = if (nextOpen != -1) afterAi.substring(nextOpen) else ""
        }

        return elements
    }

    /**
     * Parses Saudi Supermarket Variable-Weight Scale Barcode (Prefix 21 / 22)
     * Format (13 digits): PP IIII C WWWW K
     * PP: 2-digit prefix (e.g. 21)
     * IIII: 4-digit Item PLU (e.g. 1045)
     * C: PLU check digit / separator (1 digit)
     * WWWW: 4-digit weight in grams (e.g. 1450 grams = 1.450 kg)
     * K: Modulo-10 checksum
     */
    fun parseVariableWeightScaleBarcode(
        barcode: String,
        itemLookup: (String) -> Pair<String, Double>
    ): ScannedItemDetails {
        require(barcode.length == 13 && barcode.all { it.isDigit() }) {
            "Variable weight scale barcode must be exactly 13 digits"
        }
        require(Ean13CheckDigit.isValid(barcode)) {
            "Variable weight barcode failed EAN-13 check digit validation"
        }

        val plu = barcode.substring(2, 6)
        val grams = barcode.substring(7, 11).toInt()
        val weightKg = grams / 1000.0

        val (nameAr, pricePerKg) = itemLookup(plu)
        val lineTotal = Math.round(weightKg * pricePerKg * 100.0) / 100.0

        return ScannedItemDetails(
            rawBarcode = barcode,
            symbology = BarcodeSymbology.VARIABLE_WEIGHT_IN_STORE,
            itemPlu = plu,
            nameAr = nameAr,
            unitPriceSar = pricePerKg,
            quantityOrWeightKg = weightKg,
            isWeighted = true,
            lineTotalSar = lineTotal
        )
    }

    /**
     * Scans and resolves fixed EAN-13 packaged retail items
     */
    fun parseFixedEan13(
        barcode: String,
        itemLookup: (String) -> Pair<String, Double>
    ): ScannedItemDetails {
        require(Ean13CheckDigit.isValid(barcode)) {
            "EAN-13 barcode failed modulo-10 checksum verification"
        }

        val (nameAr, unitPrice) = itemLookup(barcode)

        return ScannedItemDetails(
            rawBarcode = barcode,
            symbology = BarcodeSymbology.EAN_13,
            itemPlu = barcode,
            nameAr = nameAr,
            unitPriceSar = unitPrice,
            quantityOrWeightKg = 1.0,
            isWeighted = false,
            lineTotalSar = unitPrice
        )
    }
}
