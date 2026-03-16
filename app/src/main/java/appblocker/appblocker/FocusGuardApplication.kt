package appblocker.appblocker

import android.app.Application
import appblocker.appblocker.shorts.shortsModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Minimal Application subclass.
 * Room and Repository use their own singleton companions so no init needed here.
 */
class FocusGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FocusGuardApplication)
            androidLogger()
            modules(shortsModule)
        }
    }
}
