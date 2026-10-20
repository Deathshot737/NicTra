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
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.GraphHopperConfig
import com.graphhopper.config.Profile
import com.nictra.data.local.entities.DelegacionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private val NictraBlue = Color(0xFF1A3B70)
private val NictraGreen = Color(0xFF4CAF50)
private val NictraBackground = Color(0xFFF5F7FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    delegaciones: List<DelegacionEntity>,
    initialSelectedDelegationId: String? = null,
    onHomeClick: () -> Unit,
    onMyTramitesClick: () -> Unit,
    completedCount: Int
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val leonCenter = GeoPoint(12.4378, -86.8785)

    var selectedDelegation by remember(delegaciones, initialSelectedDelegationId) {
        mutableStateOf<DelegacionEntity?>(
            delegaciones.find { it.id == initialSelectedDelegationId } ?: delegaciones.firstOrNull()
        )
    }

    var isRouting by remember { mutableStateOf(false) }
    var isPreparingData by remember { mutableStateOf(true) }
    var isHopperReady by remember { mutableStateOf(false) }
    var isMapOffline by remember { mutableStateOf(false) }
    var routeInfo by remember { mutableStateOf<String?>(null) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var selectedProfile by rememberSaveable { mutableStateOf("car") }

    val profileIcons = remember {
        mapOf(
            "car" to Icons.Default.DirectionsCar,
            "foot" to Icons.AutoMirrored.Filled.DirectionsWalk,
            "bike" to Icons.AutoMirrored.Filled.DirectionsBike
        )
    }

    val hopper = remember { GraphHopper() }

    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    val mapView = remember(context) {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidBasePath = baseDir
        Configuration.getInstance().osmdroidTileCache = File(baseDir, "osmdroid")
        
        // Cargar configuración de osmdroid
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.0)
            controller.setCenter(leonCenter)
            setUseDataConnection(false) // Deshabilitar conexión de datos para asegurar modo offline
        }
    }

    LaunchedEffect(selectedDelegation) {
        selectedDelegation?.let {
            mapView.controller.animateTo(GeoPoint(it.latitud, it.longitud))
            routePoints = null
            routeInfo = null
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            prepareOfflineFiles(context)
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val mapFile = File(baseDir, "osmdroid/leon.mbtiles")
            val ghFolder = File(baseDir, "graphhopper")

            withContext(Dispatchers.Main) {
                if (mapFile.exists() && mapFile.length() > 0) {
                    try {
                        val offlineProvider = OfflineTileProvider(SimpleRegisterReceiver(context), arrayOf(mapFile))
                        mapView.tileProvider = offlineProvider
                        
                        // Fuente de teselas para el archivo MBTiles (Mapnik es común en mbtiles)
                        val source = XYTileSource("Mapnik", 0, 22, 256, ".png", arrayOf())
                        mapView.setTileSource(source)
                        isMapOffline = true
                        Log.d("NicTra", "Mapa offline leon.mbtiles cargado correctamente")
                    } catch (e: Exception) {
                        Log.e("NicTra", "Error cargando mapa offline", e)
                        Toast.makeText(context, "Error cargando mapa offline", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("NicTra", "Archivo leon.mbtiles no encontrado en: ${mapFile.absolutePath}")
                    Toast.makeText(context, "Mapa offline no disponible", Toast.LENGTH_LONG).show()
                }
                mapView.invalidate()
            }

            val ghDataFolder = findGraphHopperDataFolder(ghFolder)
            if (ghDataFolder != null) {
                try {
                    val config = GraphHopperConfig()
                    config.putObject("datareader.file", "")
                    config.putObject("graph.location", ghDataFolder.absolutePath)
                    
                    val profiles = ArrayList<Profile>()
                    // Ajustado para GraphHopper 11.1 (vehicle se infiere o se omite si ya está en el grafo)
                    profiles.add(Profile("car").setWeighting("fastest"))
                    profiles.add(Profile("foot").setWeighting("fastest"))
                    profiles.add(Profile("bike").setWeighting("fastest"))
                    config.setProfiles(profiles)

                    hopper.init(config)
                    hopper.setAllowWrites(false)
                    val loaded = hopper.load()
                    withContext(Dispatchers.Main) { isHopperReady = loaded }
                } catch (e: Exception) { Log.e("NicTra", "Hopper Error", e) }
            }
            withContext(Dispatchers.Main) { isPreparingData = false }
        }
    }

    val locationOverlay = remember(mapView) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = NictraBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White, 
                tonalElevation = 0.dp // Evita el color morado de fondo
            ) {
                val navBarColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NictraBlue,
                    selectedTextColor = NictraBlue,
                    indicatorColor = NictraBlue.copy(alpha = 0.1f),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                    label = { Text("Inicio") },
                    selected = false,
                    onClick = onHomeClick,
                    colors = navBarColors
                )
                NavigationBarItem(
                    icon = {
                        BadgedBox(
                            badge = {
                                if (completedCount > 0) {
                                    Badge(containerColor = NictraGreen) { Text(completedCount.toString(), color = Color.White) }
                                }
                            }
                        ) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Trámites") }
                    },
                    label = { Text("Mis Trámites") },
                    selected = false,
                    onClick = onMyTramitesClick,
                    colors = navBarColors
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Map, contentDescription = "Mapa") },
                    label = { Text("Mapa") },
                    selected = true,
                    onClick = { },
                    colors = navBarColors
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(NictraBackground)
        ) {
            // CAPA 0: EL MAPA (Fondo siempre)
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize().zIndex(0f),
                update = { mv ->
                    mv.overlays.clear()
                    
                    // Dibujar la ruta si existe
                    routePoints?.let { points ->
                        val line = Polyline(mv)
                        line.setPoints(points)
                        line.outlinePaint.color = android.graphics.Color.BLUE
                        line.outlinePaint.strokeWidth = 10f
                        mv.overlays.add(line)
                    }

                    mv.overlays.add(locationOverlay)
                    
                    // Marcadores de delegaciones
                    delegaciones.forEach { delegacion ->
                        val marker = Marker(mv).apply {
                            position = GeoPoint(delegacion.latitud, delegacion.longitud)
                            title = delegacion.nombreOficina
                            snippet = delegacion.direccion
                            icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                            setOnMarkerClickListener { m, _ ->
                                selectedDelegation = delegacion
                                m.showInfoWindow()
                                true
                            }
                        }
                        mv.overlays.add(marker)
                    }
                }
            )

            // CAPA 1: Botón Volver a Mi Ubicación
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        val myLoc = locationOverlay.myLocation
                        if (myLoc != null) {
                            mapView.controller.animateTo(myLoc)
                        } else {
                            Toast.makeText(context, "Buscando GPS...", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 240.dp) // Arriba del panel de info
                    .size(48.dp),
                containerColor = Color.White,
                contentColor = NictraBlue,
                shape = RoundedCornerShape(12.dp),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Mi Ubicación")
            }

            // CAPA 2: Panel de Información de Delegación Seleccionada
            selectedDelegation?.let { delegation ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .zIndex(1f)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Encabezado con perfil de transporte
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Ruta a ${delegation.nombreOficina}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NictraBlue,
                                        maxLines = 1
                                    )
                                    routeInfo?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = NictraBlue)
                                    }
                                }
                                
                                // Selectores de Perfil
                                Row {
                                    profileIcons.forEach { (profile, icon) ->
                                        IconButton(
                                            onClick = { 
                                                selectedProfile = profile
                                                routePoints = null
                                                routeInfo = null
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(
                                                containerColor = if (selectedProfile == profile) NictraBlue.copy(alpha = 0.12f) else Color.Transparent,
                                                contentColor = if (selectedProfile == profile) NictraBlue else Color.Gray
                                            )
                                        ) {
                                            Icon(icon, contentDescription = profile)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Botón de Acción
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val myLoc = locationOverlay.myLocation
                                        if (myLoc != null) {
                                            isRouting = true
                                            val req = GHRequest(
                                                myLoc.latitude, myLoc.longitude,
                                                selectedDelegation!!.latitud, selectedDelegation!!.longitud
                                            ).setProfile(selectedProfile).setLocale(Locale.getDefault())

                                            try {
                                                val rsp = hopper.route(req)
                                                if (!rsp.hasErrors()) {
                                                    val path = rsp.best
                                                    val dist = String.format(Locale.getDefault(), "%.2f km", path.distance / 1000)
                                                    val time = String.format(Locale.getDefault(), "%d min", path.time / 60000)
                                                    
                                                    val geoPoints = ArrayList<GeoPoint>()
                                                    for (i in 0 until path.points.size()) {
                                                        geoPoints.add(GeoPoint(path.points.getLat(i), path.points.getLon(i)))
                                                    }

                                                    withContext(Dispatchers.Main) {
                                                        routeInfo = "$dist - $time"
                                                        routePoints = geoPoints
                                                        mapView.invalidate()
                                                    }
                                                } else {
                                                    val errorMsg = rsp.errors.joinToString { it.message ?: it.toString() }
                                                    Log.e("NicTra", "Routing Error: $errorMsg")
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "No se encontró ruta: ${rsp.errors[0].message ?: "Error desconocido"}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("NicTra", "Routing Exception", e)
                                            } finally {
                                                isRouting = false
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Habilite el GPS para calcular la ruta", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isRouting && isHopperReady,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NictraBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                if (isRouting) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Directions, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Calcular Ruta Offline")
                                }
                            }
                        }
                    }
                }
            }

            // Overlay de Carga Inicial
            if (isPreparingData) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).zIndex(10f),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = NictraBlue)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Preparando mapas offline...", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private fun findGraphHopperDataFolder(root: File): File? {
    if (root.exists() && root.isDirectory) {
        val children = root.list() ?: emptyArray()
        if (children.contains("nodes") || children.contains("edges") || children.contains("properties")) {
            return root
        }
        val graphCache = File(root, "graph-cache")
        if (graphCache.exists() && graphCache.isDirectory) {
            return graphCache
        }
        val files = root.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    val found = findGraphHopperDataFolder(file)
                    if (found != null) return found
                }
            }
        }
    }
    return null
}

private fun prepareOfflineFiles(context: Context) {
    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
    
    // 1. Preparar MBTiles para osmdroid
    val osmDir = File(baseDir, "osmdroid")
    if (!osmDir.exists()) osmDir.mkdirs()
    val mapFile = File(osmDir, "leon.mbtiles")
    
    // Eliminar si existe pero está vacío para forzar nueva copia
    if (mapFile.exists() && mapFile.length() == 0L) {
        mapFile.delete()
    }

    if (!mapFile.exists()) {
        try {
            Log.d("NicTra", "Copiando leon.mbtiles desde assets...")
            context.assets.open("leon.mbtiles").use { input ->
                FileOutputStream(mapFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("NicTra", "Copia exitosa de leon.mbtiles. Tamaño: ${mapFile.length()}")
        } catch (e: Exception) {
            Log.e("NicTra", "Error copiando mapa desde assets", e)
        }
    }

    // 2. Preparar GraphHopper Cache
    val ghDir = File(baseDir, "graphhopper")
    if (!ghDir.exists()) {
        ghDir.mkdirs()
        try {
            copyAssetFolder(context, "graphhopper", ghDir.absolutePath)
        } catch (e: Exception) {
            Log.e("NicTra", "Error copiando graphhopper data", e)
        }
    }
}

private fun copyAssetFolder(context: Context, assetName: String, outputPath: String) {
    val assetManager = context.assets
    val files = assetManager.list(assetName) ?: return
    
    File(outputPath).mkdirs()
    for (file in files) {
        val fullAssetName = "$assetName/$file"
        val fullOutputPath = "$outputPath/$file"
        val subFiles = assetManager.list(fullAssetName)
        if (subFiles != null && subFiles.isNotEmpty()) {
            copyAssetFolder(context, fullAssetName, fullOutputPath)
        } else {
            try {
                assetManager.open(fullAssetName).use { input ->
                    FileOutputStream(fullOutputPath).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e("NicTra", "Error copiando archivo $file", e)
            }
        }
    }
}
