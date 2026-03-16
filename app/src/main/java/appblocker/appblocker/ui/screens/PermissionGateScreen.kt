package appblocker.appblocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionGateScreen(
    usageOk: Boolean,
    overlayOk: Boolean,
    accessOk: Boolean,
    requestUsage: () -> Unit,
    requestOverlay: () -> Unit,
    requestAccessibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    var quoteIndex by remember { mutableIntStateOf(0) }
    val allGranted = usageOk && overlayOk && accessOk

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("Protect your next hour", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Finish setup before opening the app", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Why this matters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    onboardingResearchInsights.forEachIndexed { index, insight ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${index + 1}. ${insight.headline}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reality check", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(quoteForIndex(quoteIndex), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "Tap the quote to rotate. One distracted hour/day costs ~365 hours/year.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { quoteIndex += 1 }) { Text("Show another quote") }
                }
            }

            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Time cost projection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("30 min/day  -> 182.5 hours/year", style = MaterialTheme.typography.bodyMedium)
                    Text("90 min/day  -> 547.5 hours/year", style = MaterialTheme.typography.bodyMedium)
                    Text("2 hours/day -> 730 hours/year", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "That is the time price paid for passive scrolling. Permission setup is the first defense.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PermissionStepCard(
                icon = Icons.Default.Timeline,
                title = "Usage access",
                description = "Lets FocusGuard detect app usage and measure real time cost.",
                granted = usageOk,
                onGrant = requestUsage
            )
            PermissionStepCard(
                icon = Icons.Default.Lock,
                title = "Overlay permission",
                description = "Lets FocusGuard interrupt distraction instantly with a block screen.",
                granted = overlayOk,
                onGrant = requestOverlay
            )
            PermissionStepCard(
                icon = Icons.Default.Public,
                title = "Accessibility web guard",
                description = "Lets FocusGuard detect distracting web feeds and shorts pages.",
                granted = accessOk,
                onGrant = requestAccessibility
            )

            if (!allGranted) {
                Text(
                    "All three permissions are required before entering the app experience.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionStepCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onGrant) { Text("Allow") }
            }
        }
    }
}


