package com.hdfc.docupload

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dynamsoft.license.LicenseManager
import com.hdfc.docupload.util.Constants
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DocUploadApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Must run before any CaptureVisionRouter is created (document scanner).
        LicenseManager.initLicense(Constants.DYNAMSOFT_LICENSE) { isSuccess, error ->
            if (!isSuccess) error?.printStackTrace()
        }
    }
}
