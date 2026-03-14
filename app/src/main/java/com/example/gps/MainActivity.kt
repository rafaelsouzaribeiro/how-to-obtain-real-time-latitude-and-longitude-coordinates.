package com.example.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gps.ui.theme.GPSTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GPSTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

fun startLocationUpdates(
    context: Context,
    fusedClient: FusedLocationProviderClient,
    onResult: (String) -> Unit
): LocationCallback? {
    if (!hasFineLocationPermission(context)) {
        onResult("Permissão de localização não concedida.")
        return null
    }

    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        2000L
    )
        .setMinUpdateIntervalMillis(1000L)
        .build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation
            if (location != null) {
                onResult("Lat: ${location.latitude}\nLng: ${location.longitude}")
            }
        }
    }

    try {
        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    } catch (se: SecurityException) {
        onResult("Sem permissão para acessar GPS.")
        return null
    }

    return locationCallback
}

fun stopLocationUpdates(
    fusedClient: FusedLocationProviderClient,
    locationCallback: LocationCallback
) {
    fusedClient.removeLocationUpdates(locationCallback)
}


fun startTracking(
    context: Context,
    fusedClient: FusedLocationProviderClient,
    onLocationUpdate: (String) -> Unit,
    onTrackingStarted: (LocationCallback) -> Unit
) {
    val callback = startLocationUpdates(context, fusedClient, onLocationUpdate)
    if (callback != null) {
        onTrackingStarted(callback)
    }
}


fun stopTracking(
    fusedClient: FusedLocationProviderClient,
    locationCallback: LocationCallback?,
    onTrackingStopped: () -> Unit
) {
    locationCallback?.let { stopLocationUpdates(fusedClient, it) }
    onTrackingStopped()
}

@Composable
fun LocationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var locationText by remember { mutableStateOf("Toque para iniciar rastreamento") }
    var isTracking by remember { mutableStateOf(false) }
    var locationCallback by remember { mutableStateOf<LocationCallback?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            locationCallback?.let { stopLocationUpdates(fusedClient, it) }
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startTracking(
                context = context,
                fusedClient = fusedClient,
                onLocationUpdate = { locationText = it },
                onTrackingStarted = { cb ->
                    locationCallback = cb
                    isTracking = true
                }
            )
        }

        if (!granted) {
            locationText = "Permissão de localização negada."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = locationText,
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            onClick = {
                when{
                    isTracking->  stopTracking(
                        fusedClient = fusedClient,
                        locationCallback = locationCallback,
                        onTrackingStopped = {
                            locationCallback = null
                            isTracking = false
                            locationText = "Rastreamento pausado."
                        }
                    )
                    !isTracking->if (hasFineLocationPermission(context)) {
                        startTracking(
                            context = context,
                            fusedClient = fusedClient,
                            onLocationUpdate = { locationText = it },
                            onTrackingStarted = { cb ->
                                locationCallback = cb
                                isTracking = true
                            }
                        )
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(if (isTracking) "Parar Rastreamento" else "Iniciar Rastreamento")
        }
    }
}
