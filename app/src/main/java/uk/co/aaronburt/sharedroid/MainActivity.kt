package uk.co.aaronburt.sharedroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.sharedroid.ui.ShareDroidRoot
import uk.co.aaronburt.sharedroid.ui.ShareDroidTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val loaded by vm.settings.collectAsStateWithLifecycle()
            val settings = loaded ?: return@setContent
            val destinations by vm.destinations.collectAsStateWithLifecycle()

            ShareDroidTheme(settings) {
                ShareDroidRoot(vm, settings)
            }

            // Upload results arrive as notifications, so ask once there is somewhere to upload to.
            // Before Android 13 notifications need no permission, and nothing is asked.
            val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(destinations.isNotEmpty(), settings.askedForNotifications) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && destinations.isNotEmpty() && !settings.askedForNotifications) {
                    vm.update { setAskedForNotifications() }
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }
}
