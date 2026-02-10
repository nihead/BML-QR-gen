# PayMV POS Terminal - Quick Start Guide

## Getting Started

### 1. Open Project in Android Studio
```
File > Open > Select the qr-gen folder
```

### 2. Wait for Gradle Sync
- Android Studio will automatically download dependencies
- This may take a few minutes on first open

### 3. Run the Application
- Connect Android device via USB (with USB Debugging enabled) OR
- Start Android Emulator (API 24+)
- Click Run button (▶️) or press Shift+F10

### 4. Configure Settings
1. Click Settings icon (⚙️)
2. Enter password: `changeMe`
3. Fill in required fields:
   - Store Name: Your business name
   - Account Name: BML account holder name
   - Account Number: 13-digit number (e.g., 7001234567890)
   - Mobile (Optional): +960XXXXXXX format
4. Click Save

### 5. Test QR Generation
1. Return to home screen
2. Click "Generate Test QR" button
3. QR code should appear with random amount
4. Scan with BML mobile app to verify

## Project Features

✅ Real-time payment detection (polls every 2 seconds)  
✅ BML-certified QR codes with CRC-16 checksum  
✅ Encrypted settings storage  
✅ Password-protected admin access  
✅ Dark theme UI  
✅ Auto-timeout QR display (60 seconds)  
✅ Offline mode support  

## Backend API

**Base URL**: https://pos-checkout-hub.preview.emergentagent.com

**Endpoints**:
- GET `/api/payment/latest` - Poll for new payments
- POST `/api/payment/clear` - Clear displayed payment
- POST `/api/pay` - Send test payment

## Troubleshooting

**App won't build?**
- Check JDK 17 is installed
- Ensure internet connection for dependencies
- File > Invalidate Caches / Restart

**QR won't scan?**
- Verify account number is 13 digits
- Check settings are saved properly
- Ensure BML app is updated

**No payment detected?**
- Check internet connection
- Verify backend URL is accessible
- Look at Logcat for API errors

## Key Files

- `MainActivity.kt` - Main app entry point
- `PayMVQrGenerator.kt` - QR code generation logic
- `CRC16Calculator.kt` - Checksum calculation
- `IdleScreen.kt` - Home screen UI
- `QrDisplayScreen.kt` - QR display UI
- `SettingsScreen.kt` - Settings UI

## Architecture

- **Pattern**: MVVM (Model-View-ViewModel)
- **UI**: Jetpack Compose
- **Networking**: Retrofit + OkHttp
- **Storage**: EncryptedSharedPreferences
- **QR Generation**: ZXing

## Next Steps

1. Build and test the app
2. Configure with real merchant details
3. Test QR codes with BML mobile app
4. Deploy to test device
5. Monitor Logcat for any errors

For full documentation, see [README.md](README.md)
