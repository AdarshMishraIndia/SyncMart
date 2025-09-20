package com.mana.syncmart

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SyncMartApplication : Application()

// This class is required for Hilt dependency injection and should be referenced in AndroidManifest.xml
