# PayMV POS Terminal - Native Android Kotlin Rebuild Specification

## Project Overview

Build a native Android POS (Point of Sale) terminal application in Kotlin that receives payment requests via a backend API and displays BML-certified PayMV QR codes for customers to scan and complete payments.

**Current Stack (React Native)**: Expo, React Native, TypeScript  
**Target Stack (Native)**: Android Kotlin, Jetpack Compose, Material Design 3

---

## Core Features

### 1. **Payment Request Reception**
- Poll backend API every 2 seconds for new payment requests
- Backend URL: `https://pos-checkout-hub.preview.emergentagent.com/api/payment/latest`
- On payment detected, automatically navigate to QR display screen
- Clear payment from backend after displaying QR

### 2. **PayMV QR Code Generation**
- Generate BML-certified QR codes using **BML Standard TLV protocol**
- **Critical**: Must include CRC-16/CCITT-FALSE checksum (Polynomial: 0x1021, Initial: 0xFFFF)
- QR format: EMV-compliant with nested TLV structure (see technical specs below)
- Display QR code that mobile banking apps can scan

### 3. **Idle/Home Screen**
- Display store name and logo
- Show connection status indicator (green dot = ready)
- "Waiting for payment request..." message
- Access to Settings button
- Optional: Ad banner placeholders (top and bottom)
- "Test QR" button to generate random test payment

### 4. **QR Display Screen**
- Large, centered QR code
- Payment amount displayed prominently
- Store name
- Account name and number
- Auto-timeout after 60 seconds, return to idle
- "Back" button to return manually
- Keep screen awake while displaying

### 5. **Settings Screen**
- **Password-protected** (default: "changeMe")
- Configurable fields:
  - Store Name (text)
  - Store Logo (image upload/camera)
  - Account Name (text, e.g., "MOHD.NIHAD")
  - Account Number (13 digits)
  - Mobile Number (optional, Maldives format +960XXXXXXX)
  - Admin Password
  - Pro Mode toggle (hide ads)
- Save encrypted using Android KeyStore/EncryptedSharedPreferences
- Validate inputs before saving

### 6. **Additional Requirements**
- Keep screen awake during QR display
- Dark theme UI (Material Design 3)
- Portrait orientation only
- Offline-first: Cached settings work without internet
- Network state monitoring
- Error handling with user-friendly messages

---

## Technical Specifications

### PayMV QR Code Generation (BML TLV Protocol)

**Critical Implementation**: The QR code must follow EMV QR Code Specification with BML-specific extensions.

#### TLV Structure
```
Tag-Length-Value format:
- Tag: 2 digits (e.g., "00")
- Length: 2 digits (e.g., "02" for 2-character value)
- Value: Variable length data
```

#### Required Tags (in order)
1. **00**: Payload Format Indicator = "01"
2. **01**: Point of Initiation Method = "12" (dynamic QR)
3. **26**: Merchant Account Information (nested TLVs):
   - Sub-tag 00: "mv.favara.mpqr" (14 chars)
   - Sub-tag 01: Merchant ID = "MALBMVMV" (8 chars)
   - Sub-tag 02: Merchant ID = "MALBMVMV" (duplicate)
   - Sub-tag 03: **Account Number** (13 digits) - from settings
   - Sub-tag 05: Mobile number (optional, format: +960XXXXXXX)
   - Sub-tag 10: Payment type = "IPAY"
   - Sub-tag 60: Transaction reference = "POS001" (or dynamic)
4. **52**: Merchant Category Code = "0000"
5. **53**: Currency Code = "462" (MVR - Maldivian Rufiyaa)
6. **54**: Transaction Amount (formatted to 2 decimal places, e.g., "250.00")
7. **58**: Country Code = "MV"
8. **59**: Merchant Name (from settings)
9. **62**: Additional Data (timestamp or reference)
10. **63**: CRC-16 checksum (4 hex chars) = "04" + checksum

#### CRC-16 Checksum Algorithm
```kotlin
// Polynomial: 0x1021, Initial: 0xFFFF
// Calculate over entire QR string EXCEPT the checksum value itself
// QR string ends with "6304" + 4-char checksum

fun calculateCRC16(data: String): String {
    var crc = 0xFFFF
    val polynomial = 0x1021
    
    for (char in data) {
        crc = crc xor (char.code shl 8)
        for (i in 0 until 8) {
            if (crc and 0x8000 != 0) {
                crc = (crc shl 1) xor polynomial
            } else {
                crc = crc shl 1
            }
            crc = crc and 0xFFFF
        }
    }
    
    return crc.toString(16).uppercase().padStart(4, '0')
}
```

#### Example QR String
```
000201010212
26710014mv.favara.mpqr0108MALBMVMV0208MALBMVMV0313700123456789005091+9607654321010410IPAY06006POS001
52040000
5303462
5406250.00
5802MV
5912STORE NAME
62150509POS000123
6304ABCD
```

**Build Process**:
1. Construct all tags with their lengths
2. Concatenate: `000201` + `010212` + tag26 + tag52 + tag53 + tag54 + tag58 + tag59 + tag62 + `6304`
3. Calculate CRC-16 over the entire string
4. Append 4-character checksum
5. Generate QR code from final string

---

## Backend API Integration

### Base URL
```
https://pos-checkout-hub.preview.emergentagent.com
```

### Endpoints

#### 1. Poll for Payment (GET)
```http
GET /api/payment/latest
```
**Response**:
```json
{
  "amount": "250.00",
  "timestamp": "2026-02-10T08:30:00Z"
}
```
- Returns 200 if payment exists, 404 if no payment
- Poll every 2000ms (2 seconds)

#### 2. Clear Payment (POST)
```http
POST /api/payment/clear
```
Call after displaying QR to clear the payment from queue.

#### 3. Send Test Payment (POST)
```http
POST /api/pay
Content-Type: application/json

{
  "amount": "99.99"
}
```
For testing - simulates a payment webhook.

---

## Data Models

### Settings (EncryptedSharedPreferences)
```kotlin
data class AppSettings(
    val storeName: String = "PayMV Terminal",
    val storeLogo: String? = null, // File path or base64
    val accountName: String = "",
    val accountNumber: String = "", // 13 digits
    val mobileNumber: String? = null, // +960XXXXXXX
    val adminPassword: String = "changeMe",
    val proMode: Boolean = false
)
```

### Payment Request
```kotlin
data class PaymentRequest(
    val amount: String, // Format: "250.00"
    val timestamp: String
)
```

---

## UI/UX Specifications

### Screens

#### 1. Idle Screen (Home)
**Layout**:
- Top ad banner (if not pro mode) - 300dp width x 50dp height
- Store logo - 120dp x 120dp, centered
- Store name - 32sp, bold, centered
- Connection status - Row with green dot (8dp) + "Ready for payments"
- Info text - "Waiting for payment request..." - 16sp, gray
- Settings button - Bottom 16dp padding, 48dp height
- Test QR button - Below settings
- Bottom ad banner (if not pro mode)

**Colors**:
- Background: #1a1a1a
- Text: #ffffff
- Status dot: #4CAF50
- Button: #4CAF50

#### 2. QR Display Screen
**Layout**:
- Store name - Top 24sp
- QR code - 300dp x 300dp, centered, white background with 16dp padding
- Amount - "MVR 250.00" - 42sp, bold, color: #4CAF50
- Account info - "MOHD.NIHAD - 700123456789" - 14sp
- Timer countdown - "Auto-close in 45s" - 12sp, gray
- Back button - Bottom

**Auto-close**: After 60 seconds

#### 3. Settings Screen
**Layout**:
- Password dialog (on entry)
- Scrollable form with:
  - Text fields: Store Name, Account Name, Account Number, Mobile Number
  - Image picker: Store Logo (show preview)
  - Password field: Admin Password (with visibility toggle)
  - Switch: Pro Mode
- Save button - Primary action at bottom

**Validation**:
- Account Number: Exactly 13 digits
- Mobile Number: Optional, format +960XXXXXXX (7 digits after +960)
- Password: Minimum 6 characters

---

## Technology Stack

### Required Dependencies

#### Core
- Kotlin 1.9+
- Android SDK 24+ (minSdk), target SDK 35
- Jetpack Compose (UI)
- Material Design 3

#### Networking
- Retrofit 2 (REST API)
- OkHttp 4 (HTTP client)
- Coroutines + Flow (async operations)

#### Storage
- EncryptedSharedPreferences (settings)
- DataStore (preferences)

#### QR Code
- ZXing (com.google.zxing:core) - QR generation

#### Image Handling
- Coil (image loading)
- CameraX or Activity Result API (image picker)

#### Other
- Accompanist (permissions, system UI)
- Kotlin Serialization (JSON)

---

## Architecture

### Recommended Pattern: MVVM (Model-View-ViewModel)

```
app/
├── data/
│   ├── model/
│   │   ├── AppSettings.kt
│   │   └── PaymentRequest.kt
│   ├── repository/
│   │   ├── PaymentRepository.kt
│   │   └── SettingsRepository.kt
│   └── api/
│       └── PaymentApi.kt
├── ui/
│   ├── screen/
│   │   ├── IdleScreen.kt
│   │   ├── QrDisplayScreen.kt
│   │   └── SettingsScreen.kt
│   ├── viewmodel/
│   │   ├── IdleViewModel.kt
│   │   ├── QrDisplayViewModel.kt
│   │   └── SettingsViewModel.kt
│   ├── component/
│   │   ├── QrCodeView.kt
│   │   └── AdBanner.kt
│   └── theme/
│       └── Theme.kt
├── util/
│   ├── PayMVQrGenerator.kt
│   ├── CRC16Calculator.kt
│   └── NetworkMonitor.kt
└── MainActivity.kt
```

---

## Implementation Priority

### Phase 1: Core Functionality
1. ✅ Basic navigation (Idle → QR → Settings)
2. ✅ Settings screen with encrypted storage
3. ✅ PayMV QR generation with CRC-16 checksum
4. ✅ QR display with auto-timeout

### Phase 2: Backend Integration
1. ✅ Retrofit API setup
2. ✅ Payment polling service
3. ✅ Network state monitoring
4. ✅ Error handling

### Phase 3: Polish
1. ✅ Material Design 3 theming
2. ✅ Ad banner placeholders
3. ✅ Image picker for logo
4. ✅ Keep screen awake
5. ✅ Test QR button

---

## Testing Requirements

### Unit Tests
- CRC-16 checksum calculation
- PayMV QR string generation
- Settings validation

### Integration Tests
- API communication
- Settings persistence
- Payment polling cycle

### Manual Testing
- Scan generated QR with BML mobile app
- Verify payment flow end-to-end
- Test all UI screens and navigation
- Test offline functionality

---

## Build Configuration

### build.gradle.kts (app)
```kotlin
android {
    compileSdk = 35
    
    defaultConfig {
        applicationId = "com.paymv.posterminal"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.0"
    }
}

dependencies {
    // Compose
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.7.0")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // QR Code
    implementation("com.google.zxing:core:3.5.2")
    
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

---

## Environment Configuration

### Staging
- Backend: `https://pos-checkout-hub.preview.emergentagent.com`

### Production
- Backend: (To be configured)
- Require production merchant credentials

---

## Security Considerations

1. **Encrypted Storage**: Use EncryptedSharedPreferences for all settings
2. **Password Protection**: Hash admin password (don't store plaintext)
3. **API Communication**: HTTPS only
4. **QR Validation**: Verify CRC-16 before displaying
5. **Input Validation**: Sanitize all user inputs

---

## Known Limitations & Future Enhancements

### Current Limitations
- No transaction history
- No receipt printing
- Single merchant account only
- Manual settings configuration

### Future Features
- Push notifications for payments
- Transaction history with filtering
- Receipt printing via Bluetooth
- Multi-merchant support
- Analytics dashboard
- Offline payment queue

---

## Success Criteria

✅ **QR Code Scannable**: BML mobile app successfully scans and processes generated QR codes  
✅ **Real-time Updates**: Payment requests appear within 2 seconds  
✅ **Stable Operation**: No crashes during 8-hour shift  
✅ **Settings Persistence**: All settings survive app restart  
✅ **Offline Resilient**: Works with cached settings when internet drops  

---

## References

- EMV QR Code Specification: https://www.emvco.com/emv-technologies/qrcodes/
- CRC-16/CCITT-FALSE Algorithm: https://www.lammertbies.nl/comm/info/crc-calculation
- BML PayMV Documentation: (Internal/proprietary)
- Jetpack Compose: https://developer.android.com/jetpack/compose
- Material Design 3: https://m3.material.io/

---

## Development Timeline Estimate

- **Week 1**: Project setup, UI screens (Compose)
- **Week 2**: PayMV QR generation with CRC-16, settings storage
- **Week 3**: Backend integration, payment polling
- **Week 4**: Testing, bug fixes, polish

**Total Estimated Time**: 4 weeks for MVP

---

## Contact & Support

- **Backend API**: Hosted on DigitalOcean
- **QR Testing**: Use BML mobile app (Bank of Maldives)
- **Deployment**: Google Play Console (internal testing → production)
