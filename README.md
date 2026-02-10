# PayMV POS Terminal - Native Android Application

A native Android POS terminal application built with Kotlin and Jetpack Compose that generates BML-certified PayMV QR codes for payment processing.

## Features

✅ **Real-time Payment Polling** - Automatically polls backend API every 2 seconds for new payment requests  
✅ **BML-Certified QR Code Generation** - Generates compliant PayMV QR codes with CRC-16 checksum  
✅ **Encrypted Settings Storage** - Secure storage using Android EncryptedSharedPreferences  
✅ **Password-Protected Settings** - Admin password protection for configuration changes  
✅ **Pro Mode** - Ad-free experience with toggle option  
✅ **Offline Support** - Works with cached settings when internet is unavailable  
✅ **Network Monitoring** - Real-time connection status indicator  
✅ **Auto-timeout QR Display** - QR codes auto-close after 60 seconds  
✅ **Material Design 3** - Modern dark theme UI  

## Technology Stack

- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35
- **Networking**: Retrofit 2 + OkHttp
- **QR Code**: ZXing
- **Image Loading**: Coil
- **Security**: Android Security Crypto

## Project Structure

```
app/
├── data/
│   ├── model/          # Data models (AppSettings, PaymentRequest)
│   ├── api/            # Retrofit API interfaces
│   └── repository/     # Data repositories
├── ui/
│   ├── screen/         # Compose screens (Idle, QR Display, Settings)
│   ├── viewmodel/      # ViewModels for each screen
│   ├── component/      # Reusable UI components
│   ├── navigation/     # Navigation setup
│   └── theme/          # Material Design 3 theme
├── util/               # Utility classes (QR generator, CRC-16, Network Monitor)
└── MainActivity.kt     # Main activity
```

## Setup Instructions

### Prerequisites

1. **Android Studio**: Hedgehog (2023.1.1) or later
2. **JDK**: 17 or higher
3. **Android SDK**: API 35

### Installation Steps

1. **Open in Android Studio**:
   ```
   File > Open > Select the qr-gen folder
   ```

2. **Sync Gradle**:
   - Android Studio will automatically prompt to sync
   - Wait for dependencies to download

3. **Configure Backend URL** (Optional):
   - Default: `https://pos-checkout-hub.preview.emergentagent.com`
   - To change: Edit `RetrofitClient.kt` file

4. **Run the App**:
   - Connect an Android device or start an emulator
   - Click Run (▶️) button or press Shift+F10

### Initial Configuration

On first launch, configure the app:

1. Click **Settings** button (gear icon)
2. Enter default password: `changeMe`
3. Configure required settings:
   - **Store Name**: Your business name
   - **Account Name**: BML account holder name
   - **Account Number**: 13-digit BML account number
   - **Mobile Number** (Optional): Format +960XXXXXXX
   - **Admin Password**: Change default password
4. Click **Save Settings**

## Usage

### Normal Payment Flow

1. **Idle Screen**: App waits for payment requests from backend
2. **Payment Detection**: When payment is detected, automatically navigates to QR screen
3. **QR Display**: Shows QR code with amount, account details, and countdown timer
4. **Completion**: Auto-returns to idle after 60 seconds or manual back button

### Test Payment

1. On Idle screen, click **Generate Test QR** button
2. Sends random test amount to backend API
3. App detects the payment and displays QR code

### Settings Management

- Access via Settings icon on Idle screen
- Password verification required
- All settings encrypted and persisted
- Validation for account number and mobile format

## Backend API

### Base URL
```
https://pos-checkout-hub.preview.emergentagent.com
```

### Endpoints

**Get Latest Payment** (Polled every 2 seconds):
```
GET /api/payment/latest
Response: { "amount": "250.00", "timestamp": "2026-02-10T08:30:00Z" }
```

**Clear Payment** (After QR display):
```
POST /api/payment/clear
```

**Send Test Payment**:
```
POST /api/pay
Body: { "amount": "99.99" }
```

## QR Code Specification

The app generates BML-certified PayMV QR codes following:

- **Protocol**: EMV QR Code Standard with BML TLV extensions
- **Checksum**: CRC-16/CCITT-FALSE (Polynomial: 0x1021, Initial: 0xFFFF)
- **Currency**: MVR (Maldivian Rufiyaa, code: 462)
- **Format**: Dynamic QR with embedded merchant information

### QR Structure
```
- Tag 00: Payload Format Indicator
- Tag 01: Point of Initiation Method
- Tag 26: Merchant Account (nested TLVs with account info)
- Tag 52: Merchant Category Code
- Tag 53: Currency Code (462 for MVR)
- Tag 54: Transaction Amount
- Tag 58: Country Code (MV)
- Tag 59: Merchant Name
- Tag 62: Additional Data
- Tag 63: CRC-16 Checksum
```

## Testing

### Unit Tests

Run unit tests:
```bash
./gradlew test
```

Tests included:
- CRC-16 checksum calculation
- PayMV QR string generation
- Settings validation

### Manual Testing

1. **QR Code Validation**: Scan with BML mobile banking app
2. **Payment Flow**: Test end-to-end from backend to QR display
3. **Offline Mode**: Disable internet and verify cached settings work
4. **Settings Persistence**: Restart app and verify settings retained

## Security Features

- ✅ Encrypted storage for all settings
- ✅ Password protection for settings access
- ✅ HTTPS-only API communication
- ✅ CRC-16 validation for QR integrity
- ✅ Input sanitization and validation

## Build & Deployment

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### Generate Signed APK

1. Generate keystore:
   ```bash
   keytool -genkey -v -keystore paymv-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias paymv
   ```

2. Configure in `app/build.gradle.kts`:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("paymv-release.jks")
           storePassword = "your_password"
           keyAlias = "paymv"
           keyPassword = "your_password"
       }
   }
   ```

3. Build:
   ```bash
   ./gradlew assembleRelease
   ```

## Troubleshooting

### Common Issues

**1. Gradle Sync Failed**
- Ensure JDK 17 is configured
- Check internet connection for dependency downloads

**2. QR Code Not Scanning**
- Verify account number is exactly 13 digits
- Check CRC-16 checksum is calculated correctly
- Ensure BML mobile app is updated

**3. Payment Not Detected**
- Check internet connection
- Verify backend URL is accessible
- Check API permissions

**4. Settings Not Saving**
- Check storage permissions granted
- Verify Android KeyStore is available

## Future Enhancements

- 📊 Transaction history and analytics
- 🖨️ Receipt printing via Bluetooth
- 🔔 Push notifications for payments
- 📱 Multi-merchant support
- 📈 Dashboard with sales metrics
- 🌐 Offline payment queue

## License

Proprietary - PayMV POS Terminal

## Support

For issues or questions:
- Backend API: DigitalOcean hosted
- Testing: BML mobile banking app required
- Deployment: Google Play Console

---

**Version**: 1.0.0  
**Last Updated**: February 2026  
**Platform**: Android 7.0+ (API 24+)
