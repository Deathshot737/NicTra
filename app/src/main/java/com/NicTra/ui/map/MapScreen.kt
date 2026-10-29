package com.nictra.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nictra.data.local.entities.DelegacionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream

private val NictraBlue = Color(0xFF1A3B70)
private val NictraBackground = Color(0xFFF5F7FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    delegaciones: List<DelegacionEntity>,
    initialSelectedDelegationId: String? = null,
    onHomeClick: () -> Unit,
    onMyTramitesClick: () -> Unit,
    completedCount: Int,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val leonCenter = GeoPoint(12.4378, -86.8785)

    var isPreparingMap by remember { mutableStateOf(true) }

    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    val mapView = remember(context) {
        val baseDir = context.filesDir
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidBasePath = baseDir
        Configuration.getInstance().osmdroidTileCache = File(baseDir, "osmdroid")
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.0)
            controller.setCenter(leonCenter)
            setUseDataConnection(false)
        }
    }

    val locationOverlay = remember(mapView) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
        }
    }

    // Centrar en delegación si viene de detalle
    LaunchedEffect(delegaciones, initialSelectedDelegationId) {
        if (initialSelectedDelegationId != null) {
            delegaciones.find { it.id == initialSelectedDelegationId }?.let {
                mapView.controller.animateTo(GeoPoint(it.latitud, it.longitud))
                mapView.controller.setZoom(18.0)
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                prepareOfflineMap(context)
                val mapFile = File(context.filesDir, "osmdroid/leon.mbtiles")

                withContext(Dispatchers.Main) {
                    if (mapFile.exists()) {
                        try {
                            val offlineProvider = OfflineTileProvider(SimpleRegisterReceiver(context), arrayOf(mapFile))
                            mapView.tileProvider = offlineProvider
                            mapView.setTileSource(XYTileSource("Mapnik", 1, 20, 256, ".png", arrayOf()))
                            mapView.invalidate()
                            Log.d("NicTra", "Mapa offline cargado")
                        } catch (e: Exception) {
                            Log.e("NicTra", "Error en proveedor offline", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NicTra", "Error preparando archivos de mapa", e)
            } finally {
                withContext(Dispatchers.Main) { isPreparingMap = false }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mapView.onResume()
                if (hasLocationPermission) locationOverlay.enableMyLocation()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                mapView.onPause()
                locationOverlay.disableMyLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mapa de Delegaciones", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onHomeClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menú")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = NictraBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                val navBarColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NictraBlue,
                    indicatorColor = NictraBlue.copy(alpha = 0.1f)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Inicio") },
                    selected = false,
                    onClick = onHomeClick,
                    colors = navBarColors
                )
                NavigationBarItem(
                    icon = {
                        BadgedBox(badge = { if (completedCount > 0) Badge { Text(completedCount.toString()) } }) {
                            Icon(Icons.Default.List, null)
                        }
                    },
                    label = { Text("Mis Trámites") },
                    selected = false,
                    onClick = onMyTramitesClick,
                    colors = navBarColors
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Map, null) },
                    label = { Text("Mapa") },
                    selected = true,
                    onClick = { },
                    colors = navBarColors
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize().zIndex(0f),
                update = { mv ->
                    mv.overlays.clear()
                    mv.overlays.add(locationOverlay)

                    delegaciones.forEach { del ->
                        val marker = Marker(mv).apply {
                            position = GeoPoint(del.latitud, del.longitud)
                            title = del.nombreOficina
                            snippet = del.direccion
                            icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                            setOnMarkerClickListener { m, _ ->
                                m.showInfoWindow()
                                true
                            }
                        }
                        mv.overlays.add(marker)
                    }
                }
            )

            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        locationOverlay.myLocation?.let {
                            mapView.controller.animateTo(it)
                            mapView.controller.setZoom(17.0)
                        } ?: Toast.makeText(context, "Buscando GPS...", Toast.LENGTH_SHORT).show()
                    } else {
                        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación", tint = NictraBlue)
            }

            if (isPreparingMap) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).zIndex(10f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

private fun prepareOfflineMap(context: Context) {
    val osmDir = File(context.filesDir, "osmdroid").apply { if (!exists()) mkdirs() }
    val mapFile = File(osmDir, "leon.mbtiles")
    if (!mapFile.exists()) {
        try {
            context.assets.open("leon.mbtiles").use { input ->
                FileOutputStream(mapFile).use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.e("NicTra", "Error copiando mapa", e)
        }
    }
}
