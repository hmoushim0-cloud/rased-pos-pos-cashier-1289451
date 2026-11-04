# تطبيق راصد كاشير — باحث متخصص في دراسة الجدوى ا (`rased-pos-pos-cashier-1289451`)
> منظومة pos_cashier المتكاملة مع ربط ESC/POS Thermal Receipt Printer + Barcode Scanner

- **Project ID**: `#1289451`
- **Commercial Archetype**: `pos_cashier`
- **Bound Hardware Peripheral**: `ESC/POS Thermal Receipt Printer + Barcode Scanner`
- **Package Name**: `sa.factory.rasedposposcashier`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)

## [Sidekick Autonomous Update]

### 1. Executive Summary & Architecture Modernization
Sidekick autonomously enhanced the `rased-pos-pos-cashier-1289451` codebase (Retail POS, Supermarket Checkout & Cashier Archetype) by implementing a full GS1-128 and EAN-13 barcode scanner engine with Saudi in-store variable-weight scale parsing, an ESC/POS hardware cash drawer kicker pulse generator, a multi-tender split payment settlement processor (Cash, Mada, Apple Pay), and an automated cashier shift reconciliation engine (X-Report and Z-Report) with 15% ZATCA VAT tracking.

### 2. Newly Built Features & Core Modules

#### A. GS1-128 & Variable-Weight Barcode Scanner Engine (`Gs1BarcodeScannerEngine.kt`)
- **EAN-13 Modulo-10 Check Digit Verification**: Implements odd/even weighting and validation for global retail barcodes.
- **GS1-128 Application Identifier (AI) Parser**: Decodes bracketed supply chain barcodes, extracting GTIN-14 `(01)`, Batch/Lot Number `(10)`, Expiration Date `(17)`, and Net Weight in kg `(3102)`.
- **Saudi Variable-Weight Scale Barcode Resolution**: Supports supermarket scale barcodes (prefixes `20` through `29`), extracting 4-digit item PLU codes, embedded gram weights, and calculating exact line totals at specified price-per-kg rates.

#### B. Multi-Tender Cashier Settlement & Shift Reconciliation Engine (`CashierTenderSettlementEngine.kt`)
- **Multi-Tender Split Payments**: Handles split billing across `CASH`, `MADA_DEBIT`, `APPLE_PAY`, and `LOYALTY_POINTS` with change calculation in Halalas.
- **Hardware Cash Drawer Pulse Frame**: Generates standard ESC/POS solenoid kick commands (`ESC p 0 25 250`) on Pin 2 and Pin 5.
- **Cashier Shift Lifecycle & Reconciliation**: Opens cashier trading sessions with float tracking, accumulates gross sales and 15% ZATCA VAT across payment tenders, and executes End-of-Day Z-Reports calculating expected drawer cash, counted cash, and cash variance (overage/shortage).

#### C. Upgraded Hardware Driver Stream (`HardwareSensorDriver.kt`)
- Live simulation of USB HID barcode scanning events, GS1 symbology recognition, and ESC/POS drawer kicker status telemetry.

### 3. Verification & Test Suite
Comprehensive JUnit 4 test suite (`RasedPosCashierFeatureTest.kt`) verified:
- EAN-13 modulo-10 check digit calculation and corrupted digit rejection.
- GS1-128 multi-AI data element extraction.
- Saudi retail variable-weight scale barcode parsing and line price calculation.
- ESC/POS cash drawer kick command byte formatting.
- Multi-tender split payment calculation and exact cash change determination.
- End-of-Day Z-Report shift reconciliation and cash variance tracking.

### 4. Build & Execution Guide
```bash
# Set Java 17 and Kotlin classpath
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH
CP="/usr/share/java/junit4-4.13.2.jar:/usr/share/java/hamcrest-2.2.jar:/usr/share/java/kotlin-stdlib.jar"

# Compile Kotlin modules
kotlinc -Xskip-metadata-version-check -cp "$CP" -d /tmp/rased_test_bin \
  app/src/main/java/sa/factory/rasedposposcashier/hardware/barcode/Gs1BarcodeScannerEngine.kt \
  app/src/main/java/sa/factory/rasedposposcashier/domain/cashier/CashierTenderSettlementEngine.kt \
  app/src/test/java/sa/factory/rasedposposcashier/RasedPosCashierFeatureTest.kt

# Run verification suite
java -cp "/tmp/rased_test_bin:$CP" org.junit.runner.JUnitCore sa.factory.rasedposposcashier.RasedPosCashierFeatureTest
```

