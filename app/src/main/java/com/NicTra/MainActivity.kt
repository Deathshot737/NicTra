package com.nictra

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nictra.data.local.AppDatabase
import com.nictra.data.local.SyncPreferences
import com.nictra.data.local.entities.CategoriaEntity
import com.nictra.data.remote.NetworkModule
import com.nictra.data.repository.SyncRepository
import com.nictra.data.repository.TramiteRepository
import com.nictra.ui.home.HomeScreen
import com.nictra.ui.map.MapScreen
import com.nictra.ui.tramites.MyTramitesScreen
import com.nictra.ui.tramites.TramiteDetailScreen
import com.nictra.ui.tramites.TramitesListScreen
import com.nictra.ui.welcome.WelcomeScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

private val NictraBlue = Color(0xFF1A3B70)
private val NictraGreen = Color(0xFF4CAF50)
private val NictraBackground = Color(0xFFF5F7FA)

private val NicTraColorScheme = lightColorScheme(
    primary = NictraBlue,
    onPrimary = Color.White,
    primaryContainer = NictraBlue.copy(alpha = 0.1f),
    onPrimaryContainer = NictraBlue,
    secondary = NictraGreen,
    onSecondary = Color.White,
    secondaryContainer = NictraBlue.copy(alpha = 0.12f),
    onSecondaryContainer = NictraBlue,
    tertiary = Color(0xFFFBC02D),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    background = NictraBackground,
    surfaceVariant = Color.White,
    surfaceTint = Color.Transparent
)

@Composable
fun NicTraTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = NictraBlue.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = NicTraColorScheme,
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        val db = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        val syncPrefs = SyncPreferences(applicationContext)
        val repository = TramiteRepository(db.tramiteDao(), syncPrefs)
        val syncRepository = SyncRepository(NetworkModule.apiService, db.tramiteDao(), syncPrefs)

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            var isSyncing by remember { mutableStateOf(false) }
            var syncFailed by remember { mutableStateOf(false) }

            // Navigation states
            var showWelcome by remember { mutableStateOf(true) }
            var selectedCategory by remember { mutableStateOf<CategoriaEntity?>(null) }
            var selectedTramiteId by remember { mutableStateOf<String?>(null) }
            var showMyTramites by remember { mutableStateOf(false) }
            var showMap by remember { mutableStateOf(false) }
            var selectedDelegationId by remember { mutableStateOf<String?>(null) }

            // Drawer State
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            // Synchronization observer
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        lifecycleScope.launch {
                            isSyncing = true
                            val lastSync = syncPrefs.ultimaActualizacion.first()
                            val result = syncRepository.syncFeed(force = lastSync == null)
                            syncFailed = result.isFailure
                            isSyncing = false
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Data for Drawer and Shared use
            val categorias by repository.getAllCategorias().collectAsState(initial = emptyList())
            val completedCount by repository.getCompletedTramitesCount().collectAsState(initial = 0)
            val lastSyncDate by repository.lastSyncDate.collectAsState(initial = null)
            
            var searchQuery by remember { mutableStateOf("") }
            val searchResults by repository.searchTramites(searchQuery).collectAsState(initial = emptyList())

            NicTraTheme {
                // RTL provider to make the Drawer slide from the RIGHT
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = !showWelcome,
                        drawerContent = {
                            // Back to LTR for the drawer content itself
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                ModalDrawerSheet(
                                    modifier = Modifier.fillMaxHeight().width(300.dp),
                                    drawerContainerColor = Color.White
                                ) {
                                    DrawerHeader()
                                    
                                    Column(
                                        modifier = Modifier
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 12.dp)
                                    ) {
                                        Text(
                                            "MENÚ PRINCIPAL", 
                                            modifier = Modifier.padding(16.dp), 
                                            fontSize = 12.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            color = Color.Gray
                                        )
                                        
                                        DrawerItem(label = "Inicio", icon = Icons.Default.Home, onClick = {
                                            scope.launch { drawerState.close() }
                                            showMap = false; showMyTramites = false; selectedCategory = null; selectedTramiteId = null
                                        })
                                        
                                        DrawerItem(label = "Mis Trámites", icon = Icons.Default.List, badgeCount = completedCount, onClick = {
                                            scope.launch { drawerState.close() }
                                            showMap = false; showMyTramites = true; selectedCategory = null; selectedTramiteId = null
                                        })
                                        
                                        DrawerItem(label = "Mapa de Delegaciones", icon = Icons.Default.Place, onClick = {
                                            scope.launch { drawerState.close() }
                                            showMap = true; showMyTramites = false; selectedCategory = null; selectedTramiteId = null
                                        })

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "CATEGORÍAS", 
                                            modifier = Modifier.padding(16.dp), 
                                            fontSize = 12.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            color = Color.Gray
                                        )
                                        
                                        categorias.forEach { cat ->
                                            DrawerItem(label = cat.nombre, emoji = cat.icono, onClick = {
                                                scope.launch { drawerState.close() }
                                                selectedCategory = cat; showMap = false; showMyTramites = false; selectedTramiteId = null
                                            })
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "ACCIONES", 
                                            modifier = Modifier.padding(16.dp), 
                                            fontSize = 12.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            color = Color.Gray
                                        )
                                        
                                        DrawerItem(label = "Sincronizar ahora", icon = Icons.Default.Refresh, onClick = {
                                            scope.launch { 
                                                drawerState.close()
                                                isSyncing = true
                                                syncRepository.syncFeed(force = true)
                                                isSyncing = false
                                            }
                                        })
                                        
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Text(
                                            "NicTra Nicaragua v1.0.1", 
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), 
                                            textAlign = TextAlign.Center, 
                                            fontSize = 11.sp, 
                                            color = Color.LightGray
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        // Main content must be LTR
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                when {
                                    showWelcome -> {
                                        WelcomeScreen(onNavigateToHome = { showWelcome = false })
                                    }
                                    selectedTramiteId != null -> {
                                        val tramite by repository.getTramiteByIdFlow(selectedTramiteId!!).collectAsState(initial = null)
                                        val requisitos by repository.getRequisitosByTramite(selectedTramiteId!!).collectAsState(initial = emptyList())
                                        val pasos by repository.getPasosByTramite(selectedTramiteId!!).collectAsState(initial = emptyList())
                                        val delegaciones by repository.getDelegacionesByTramite(selectedTramiteId!!).collectAsState(initial = emptyList())
                                        
                                        tramite?.let {
                                            TramiteDetailScreen(
                                                tramite = it,
                                                requisitos = requisitos,
                                                pasos = pasos,
                                                delegaciones = delegaciones,
                                                onBackClick = { selectedTramiteId = null },
                                                onRequisitoCheckChange = { reqId, checked -> lifecycleScope.launch { repository.updateRequisitoStatus(reqId, checked) } },
                                                onCompleteClick = { completed -> lifecycleScope.launch { repository.updateCompletionStatus(selectedTramiteId!!, completed) } },
                                                onResetClick = { lifecycleScope.launch { repository.resetTramite(selectedTramiteId!!) } },
                                                onStartTracking = { lifecycleScope.launch { repository.markTramiteAsVisited(selectedTramiteId!!) } },
                                                onDelegationClick = { delId -> selectedDelegationId = delId; showMap = true; selectedTramiteId = null },
                                                onSaveClick = { /* Handled via Room Flow updates */ }
                                            )
                                        }
                                    }
                                    showMap -> {
                                        val allDelegaciones by repository.getAllDelegaciones().collectAsState(initial = emptyList())
                                        MapScreen(
                                            delegaciones = allDelegaciones,
                                            initialSelectedDelegationId = selectedDelegationId,
                                            onHomeClick = { showMap = false; selectedDelegationId = null },
                                            onMyTramitesClick = { showMyTramites = true; showMap = false; selectedDelegationId = null },
                                            completedCount = completedCount,
                                            onMenuClick = { scope.launch { drawerState.open() } }
                                        )
                                    }
                                    showMyTramites -> {
                                        val myTramitesProgress by repository.getMyTramitesWithProgress().collectAsState(initial = emptyList())
                                        MyTramitesScreen(
                                            tramitesProgress = myTramitesProgress,
                                            onTramiteClick = { selectedTramiteId = it },
                                            onHomeClick = { showMyTramites = false },
                                            onMapClick = { showMap = true; showMyTramites = false },
                                            completedCount = completedCount,
                                            onMenuClick = { scope.launch { drawerState.open() } }
                                        )
                                    }
                                    selectedCategory != null -> {
                                        val categoryTramites by repository.getTramitesByCategoria(selectedCategory!!.id).collectAsState(initial = emptyList())
                                        TramitesListScreen(
                                            categoriaNombre = selectedCategory!!.nombre,
                                            tramites = categoryTramites,
                                            onBackClick = { selectedCategory = null },
                                            onTramiteClick = { selectedTramiteId = it }
                                        )
                                    }
                                    else -> {
                                        val quickAccess by repository.getQuickAccessTramites().collectAsState(initial = emptyList())
                                        val recentTramites by repository.getRecentTramites().collectAsState(initial = emptyList())
                                        HomeScreen(
                                            categorias = categorias,
                                            quickAccess = quickAccess,
                                            recentTramites = recentTramites,
                                            searchResults = searchResults,
                                            searchQuery = searchQuery,
                                            lastSyncDate = lastSyncDate,
                                            isSyncing = isSyncing,
                                            syncFailed = syncFailed,
                                            onSearchQueryChange = { searchQuery = it },
                                            completedCount = completedCount,
                                            onCategoryClick = { selectedCategory = it },
                                            onTramiteClick = { selectedTramiteId = it },
                                            onMyTramitesClick = { showMyTramites = true },
                                            onMapClick = { showMap = true },
                                            onSyncClick = { lifecycleScope.launch { isSyncing = true; syncRepository.syncFeed(force = true); isSyncing = false } },
                                            onMenuClick = { scope.launch { drawerState.open() } }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerHeader() {
    Box(modifier = Modifier.fillMaxWidth().background(NictraBlue).padding(24.dp)) {
        Column {
            Surface(modifier = Modifier.size(60.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, modifier = Modifier.size(36.dp), tint = Color.White) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("NicTra Nicaragua", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Servicios Ciudadanos", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

@Composable
fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, emoji: String? = null, badgeCount: Int = 0, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontWeight = FontWeight.Medium)
                if (badgeCount > 0) {
                    Surface(color = NictraBlue, shape = CircleShape) {
                        Text(badgeCount.toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        icon = {
            if (icon != null) Icon(icon, null, tint = NictraBlue)
            else if (emoji != null) Text(emoji, fontSize = 20.sp)
        },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
    )
}
