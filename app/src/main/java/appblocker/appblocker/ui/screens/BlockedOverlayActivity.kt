package appblocker.appblocker.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class BlockedOverlayActivity : ComponentActivity() {

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_DISMISS) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE)
        val blockedDomain  = intent.getStringExtra(EXTRA_DOMAIN)

        // Register dismiss broadcast
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, IntentFilter(ACTION_DISMISS), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(dismissReceiver, IntentFilter(ACTION_DISMISS))
        }

        // Intercept back press — go home instead of back to blocked app
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            BlockedScreen(
                blockedPackage = blockedPackage,
                blockedDomain  = blockedDomain,
                onGoHome       = ::goHome
            )
        }
    }

    // onNewIntent fires when FLAG_ACTIVITY_SINGLE_TOP brings this activity
    // back to front (different blocked app). Update the displayed info.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Compose state will reread from intent on next recomposition
    }

    override fun onDestroy() {
        unregisterReceiver(dismissReceiver)
        super.onDestroy()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE  = "extra_package"
        const val EXTRA_DOMAIN   = "extra_domain"
        const val ACTION_DISMISS = "appblocker.appblocker.DISMISS_OVERLAY"
    }
}

@Composable
fun BlockedScreen(
    blockedPackage: String?,
    blockedDomain: String?,
    onGoHome: () -> Unit
) {
    val subject = blockedDomain ?: blockedPackage ?: "This"
    val isWeb   = blockedDomain != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0000000)),   // near-opaque, not fully black so user knows what's under
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Text("🚫", fontSize = 64.sp)

            Text(
                text = if (isWeb) "Website Blocked" else "App Blocked",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subject,
                color = Color(0xFFAAAAAA),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Blocked by your FocusGuard schedule.",
                color = Color(0xFF777777),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Button(onClick = onGoHome) {
                Text("Go Home")
            }
        }
    }
}
