Current Architecture Summary

Your implementation consists of:

| Component          | Purpose                                                                  |
| ------------------ | ------------------------------------------------------------------------ |
| `AlarmModule.kt`   | React Native bridge - schedules/cancels alarms via AlarmManager          |
| `AlarmReceiver.kt` | BroadcastReceiver - plays sound, shows notification, handles stop/snooze |
| `BootReceiver.kt`  | Reschedules alarms after reboot/time changes                             |
| `AlarmPackage.kt`  | Registers native module with React Native                                |
| `alarm-module.ts`  | TypeScript interface for JavaScript layer                                |

---

## Step-by-Step Extraction Plan

### Step 1: Create the Package Structure

```/dev/null/structure.txt#L1-23
react-native-alarmageddon/
├── package.json
├── README.md
├── tsconfig.json
├── react-native.config.js          # Autolinking configuration
├── src/
│   └── index.ts                    # TypeScript API (your alarm-module.ts)
│   └── index.d.ts                  # Type declarations
├── android/
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml     # Permissions & receivers
│   │   ├── res/raw/
│   │   │   └── alarm_default.wav   # Default alarm sound
│   │   └── java/com/rnalarmmodule/
│   │       ├── AlarmModule.kt
│   │       ├── AlarmPackage.kt
│   │       ├── AlarmReceiver.kt
│   │       └── BootReceiver.kt
└── ios/                             # iOS stub/implementation
    ├── RNAlarmModule.xcodeproj/
    └── RNAlarmModule.swift
```

---

### Step 2: Key Changes Required

#### A. Decouple from MainApplication (Critical!)

Your `AlarmReceiver.kt` currently depends on `MainApplication.getReactContext()`:

```my-meds-times/android/app/src/main/java/com/alamedapps/mymedstimes/AlarmReceiver.kt#L226-232
    private fun emitActiveAlarmId(id: String?) {
      val reactContext = MainApplication.getReactContext()

      if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
        reactContext
          .getJSModule(RCTDeviceEventEmitter::class.java)
          .emit("activeAlarmId", id)
```

**Solution**: Move the static context holder to `AlarmModule` itself:

```/dev/null/AlarmModule.kt#L1-25
package com.rnalarmmodule

import com.facebook.react.bridge.*
import java.lang.ref.WeakReference

class AlarmModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val PREFS = "rn_alarm_module_alarms"
        private const val DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"

        // Weak reference to avoid memory leaks
        private var reactContextRef: WeakReference<ReactApplicationContext>? = null

        fun getReactContext(): ReactApplicationContext? = reactContextRef?.get()
    }

    init {
        // Update the reference whenever the module is instantiated
        reactContextRef = WeakReference(reactContext)
    }

    override fun getName(): String = "AlarmModule"

    // ... rest of the methods
}
```

Then update `AlarmReceiver.kt` to use:

```/dev/null/AlarmReceiver.kt#L1-10
private fun emitActiveAlarmId(id: String?) {
    val reactContext = AlarmModule.getReactContext()

    if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
        reactContext
            .getJSModule(RCTDeviceEventEmitter::class.java)
            .emit("activeAlarmId", id)
    }
}
```

---

#### B. Change Package Names

Replace `com.alamedapps.mymedstimes` with a generic name like `com.rnalarmmodule` throughout all Kotlin files.

---

#### C. Create Library-Style `build.gradle`

```/dev/null/android/build.gradle#L1-45
buildscript {
    ext.kotlin_version = '1.9.22'
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}

apply plugin: 'com.android.library'
apply plugin: 'kotlin-android'

android {
    compileSdkVersion 34

    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 34
    }

    sourceSets {
        main {
            java.srcDirs = ['src/main/java']
            res.srcDirs = ['src/main/res']
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    implementation 'com.facebook.react:react-android'
    implementation 'androidx.core:core-ktx:1.12.0'
}
```

---

#### D. Create AndroidManifest for the Library

```/dev/null/android/src/main/AndroidManifest.xml#L1-22
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.rnalarmmodule">

    <!-- Permissions that will merge with host app -->
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <receiver android:name=".AlarmReceiver" android:exported="false" />

        <receiver android:name=".BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

---

#### E. Create `react-native.config.js` for Autolinking

```/dev/null/react-native.config.js#L1-11
module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath: 'import com.rnalarmmodule.AlarmPackage;',
        packageInstance: 'new AlarmPackage()',
      },
      ios: null, // or configure for iOS when implemented
    },
  },
};
```

---

#### F. Create `package.json`

```/dev/null/package.json#L1-42
{
  "name": "react-native-alarmageddon",
  "version": "1.0.0",
  "description": "Native exact alarm scheduling for React Native with sound, snooze, and boot persistence",
  "main": "lib/index.js",
  "module": "lib/index.js",
  "types": "lib/index.d.ts",
  "react-native": "src/index.ts",
  "source": "src/index.ts",
  "files": [
    "lib/",
    "src/",
    "android/",
    "ios/",
    "react-native.config.js",
    "README.md"
  ],
  "scripts": {
    "build": "tsc",
    "prepare": "npm run build",
    "prepublishOnly": "npm run build"
  },
  "keywords": [
    "react-native",
    "alarm",
    "scheduler",
    "notification",
    "android",
    "exact-alarm"
  ],
  "author": "Your Name",
  "license": "MIT",
  "peerDependencies": {
    "react": ">=16.8.0",
    "react-native": ">=0.60.0"
  },
  "devDependencies": {
    "@types/react": "^18.0.0",
    "@types/react-native": "^0.72.0",
    "typescript": "^5.0.0"
  }
}
```

---

#### G. Enhance TypeScript Layer with Event Listener

```/dev/null/src/index.ts#L1-85
import { NativeModules, NativeEventEmitter, PermissionsAndroid, Platform } from 'react-native';

export type AlarmParams = {
  id: string;
  datetimeISO: string;
  title?: string;
  body?: string;
};

export type AlarmModuleConfig = {
  defaultSnoozeMinutes?: number;
  autoStopSeconds?: number;
};

interface AlarmModuleInterface {
  scheduleAlarm(alarm: AlarmParams): Promise<void>;
  cancelAlarm(id: string): Promise<void>;
  listAlarms(): Promise<AlarmParams[]>;
  requestPermissions(): Promise<{ granted: boolean }>;
  snoozeAlarm(id: string, minutes: number): Promise<void>;
  stopCurrentAlarm(id: string): Promise<void>;
  snoozeCurrentAlarm(id: string, minutes: number): Promise<void>;
  getCurrentAlarmPlaying(): Promise<{ activeAlarmId: string } | null>;
}

const { AlarmModule } = NativeModules as { AlarmModule: AlarmModuleInterface };

// Validate module exists
if (!AlarmModule) {
  throw new Error(
    'react-native-alarmageddon: NativeModule is null. ' +
    'Ensure you have linked the native module correctly.'
  );
}

const eventEmitter = new NativeEventEmitter(NativeModules.AlarmModule);

async function ensureNotificationPermission(): Promise<boolean> {
  if (Platform.OS !== 'android') return true;

  const status = await AlarmModule.requestPermissions();
  if (status.granted) return true;

  if (Platform.Version >= 33) {
    const req = await PermissionsAndroid.request(
      'android.permission.POST_NOTIFICATIONS' as any
    );
    return req === PermissionsAndroid.RESULTS.GRANTED;
  }
  return true;
}

// Event subscription helper
function onAlarmStateChange(callback: (alarmId: string | null) => void) {
  const subscription = eventEmitter.addListener('activeAlarmId', callback);
  return () => subscription.remove();
}

const RNAlarmModule = {
  ensureNotificationPermission,
  scheduleAlarm: (alarm: AlarmParams) => AlarmModule.scheduleAlarm(alarm),
  cancelAlarm: (id: string) => AlarmModule.cancelAlarm(id),
  listAlarms: () => AlarmModule.listAlarms(),
  requestPermissions: () => AlarmModule.requestPermissions(),
  snoozeAlarm: (id: string, minutes: number) => AlarmModule.snoozeAlarm(id, minutes),
  stopCurrentAlarm: (id: string) => AlarmModule.stopCurrentAlarm(id),
  snoozeCurrentAlarm: (id: string, minutes: number) => AlarmModule.snoozeCurrentAlarm(id, minutes),
  getCurrentAlarmPlaying: () => AlarmModule.getCurrentAlarmPlaying(),
  onAlarmStateChange,
};

export default RNAlarmModule;
export { RNAlarmModule };
```

---

### Step 3: iOS Stub (Minimum Viable)

At minimum, create a stub so apps don't crash on iOS:

```/dev/null/ios/RNAlarmModule.m#L1-35
#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(AlarmModule, NSObject)

RCT_EXTERN_METHOD(scheduleAlarm:(NSDictionary *)alarm
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cancelAlarm:(NSString *)alarmId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(listAlarms:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(requestPermissions:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(stopCurrentAlarm:(NSString *)alarmId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(snoozeCurrentAlarm:(NSString *)alarmId
                  minutes:(nonnull NSNumber *)minutes
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getCurrentAlarmPlaying:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
```

---

### Step 4: Documentation (README.md)

Create comprehensive docs covering:

1. **Installation**: `npm install react-native-alarmageddon`
2. **Android Setup**: Required permissions (SCHEDULE_EXACT_ALARM needs runtime request on Android 12+)
3. **Usage Examples**
4. **API Reference**
5. **Limitations** (Android only for now, exact alarm permissions on Android 12+)

---

## Summary Checklist

| Task                                                                         | Status |
| ---------------------------------------------------------------------------- | ------ |
| Create new repository/folder structure                                       | ✅     |
| Copy and refactor AlarmModule.kt (change package, add static context holder) | ✅     |
| Copy and refactor AlarmReceiver.kt (use AlarmModule.getReactContext())       | ✅     |
| Copy and refactor BootReceiver.kt (change package name)                      | ✅     |
| Copy and refactor AlarmPackage.kt                                            | ✅     |
| Create library build.gradle                                                  | ✅     |
| Create library AndroidManifest.xml                                           | ✅     |
| Enhance TypeScript with NativeEventEmitter                                   | ✅     |
| Create react-native.config.js                                                | ✅     |
| Create package.json                                                          | ✅     |
| Copy alarm sound to res/raw/                                                 | ⚠️ (Placeholder README added - uses system default) |
| Create iOS stubs                                                             | ✅     |
| Write README.md                                                              | ✅     |
| Test in a fresh React Native project                                         | ⬜     |
| Publish to npm                                                               | ⬜     |

### Additional Components Created

| Component          | Purpose                                                                  |
| ------------------ | ------------------------------------------------------------------------ |
| `AlarmService.kt`  | Foreground service for playing alarm sound and showing notification      |
| `AlarmActivity.kt` | Transparent activity to wake screen and launch app                       |
| `AlarmModule.swift`| iOS Swift stub implementation                                            |
| `*.podspec`        | CocoaPods specification for iOS integration                              |
| `LICENSE`          | MIT License file                                                         |
| `.gitignore`       | Git ignore patterns                                                      |
| `.npmignore`       | NPM publish ignore patterns                                              |