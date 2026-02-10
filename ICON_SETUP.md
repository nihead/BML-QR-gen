# App Icon Setup Guide

## Current Status

The project uses default Android launcher icons. To customize:

## Option 1: Android Studio Image Asset Tool (Recommended)

1. Right-click on `app/src/main/res` folder
2. Select **New > Image Asset**
3. In Asset Studio:
   - **Icon Type**: Launcher Icons (Adaptive and Legacy)
   - **Name**: ic_launcher
   - **Asset Type**: Choose one:
     - **Image**: Upload your logo file (PNG/JPEG)
     - **Clip Art**: Use built-in icons
     - **Text**: Generate from text
4. Configure:
   - **Foreground Layer**: Your icon/logo
   - **Background Layer**: Solid color or image
5. Click **Next** then **Finish**

## Option 2: Manual Icon Creation

Create icons in these sizes and place in respective folders:

```
app/src/main/res/
├── mipmap-mdpi/
│   ├── ic_launcher.png (48x48)
│   └── ic_launcher_round.png (48x48)
├── mipmap-hdpi/
│   ├── ic_launcher.png (72x72)
│   └── ic_launcher_round.png (72x72)
├── mipmap-xhdpi/
│   ├── ic_launcher.png (96x96)
│   └── ic_launcher_round.png (96x96)
├── mipmap-xxhdpi/
│   ├── ic_launcher.png (144x144)
│   └── ic_launcher_round.png (144x144)
└── mipmap-xxxhdpi/
    ├── ic_launcher.png (192x192)
    └── ic_launcher_round.png (192x192)
```

## Option 3: Adaptive Icon (Android 8.0+)

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

## Recommended Specifications

- **Format**: PNG with transparency
- **Minimum Size**: 512x512 px for source
- **Safe Zone**: Keep important content in center 66%
- **Colors**: Use brand colors matching your store theme
- **Design**: Simple, recognizable icon works best

## Icon Ideas for PayMV POS

Consider icons that represent:
- 💳 Payment/Transaction
- 📱 Mobile payments
- 🏪 Store/Shop
- 💰 Money/Currency
- 📊 POS Terminal

## Testing Icons

1. After adding icons, rebuild the app
2. Uninstall old version from device
3. Install fresh build
4. Check home screen and app drawer
5. Verify icon looks good at different sizes

## Online Tools

Generate icons using these free tools:
- **Android Asset Studio**: https://romannurik.github.io/AndroidAssetStudio/
- **App Icon Generator**: https://appicon.co/
- **Icon Kitchen**: https://icon.kitchen/

## Current Default Icons

The app currently uses Android's default green adaptive icons. Replace them to match your PayMV brand identity!
