package appblocker.appblocker

import android.app.Application

/**
 * Minimal Application subclass.
 * Room and Repository use their own singleton companions so no init needed here.
 */
class FocusGuardApplication : Application()
