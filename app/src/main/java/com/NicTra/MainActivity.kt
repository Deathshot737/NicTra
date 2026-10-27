package com.nictra

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nictra.data.local.AppDatabase
import com.nictra.data.local.SyncPreferences
import com.nictra.data.local.entities.CategoriaEntity
import com.nictra.data.local.entities.TramiteEntity
import com.nictra.data.remote.NetworkModule
import com.nictra.data.repository.SyncRepository
import com.nictra.data.repository.TramiteRepository
import com.nictra.ui.home.HomeScreen
import com.nictra.ui.map.MapScreen
import com.nictra.ui.tramites.MyTramitesScreen
import com.nictra.ui.tramites.TramiteDetailScreen
import com.nictra.ui.tramites.TramitesListScreen
import com.nictra.ui.welcome.WelcomeScreen
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

            // Observador para sincronizar al entrar en primer plano
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        lifecycleScope.launch {
                            isSyncing = true
                            val result = syncRepository.syncFeed()
                            syncFailed = result.isFailure
                            isSyncing = false
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            var showWelcome by remember { mutableStateOf(true) }
            var selectedCategory by remember { mutableStateOf<CategoriaEntity?>(null) }
            var selectedTramiteId by remember { mutableStateOf<String?>(null) }
            var showMyTramites by remember { mutableStateOf(false) }
            var showMap by remember { mutableStateOf(false) }
            var selectedDelegationId by remember { mutableStateOf<String?>(null) }
            
            var searchQuery by remember { mutableStateOf("") }
            val searchResults by repository.searchTramites(searchQuery).collectAsState(initial = emptyList())
            val lastSyncDate by repository.lastSyncDate.collectAsState(initial = null)

            NicTraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        showWelcome -> WelcomeScreen(onNavigateToHome = { showWelcome = false })
                        selectedTramiteId != null -> {
                            BackHandler { selectedTramiteId = null }
                            val id = selectedTramiteId!!
                            
                            // Cambiado produceState por collectAsState para observar cambios en tiempo real
                            val tramite by repository.getTramiteByIdFlow(id).collectAsState(initial = null)

                            val requisitos by repository.getRequisitosByTramite(id).collectAsState(initial = emptyList())
                            val pasos by repository.getPasosByTramite(id).collectAsState(initial = emptyList())
                            val delegaciones by repository.getDelegacionesByTramite(id).collectAsState(initial = emptyList())

                            if (tramite != null) {
                                TramiteDetailScreen(
                                    tramite = tramite!!,
                                    requisitos = requisitos,
                                    pasos = pasos,
                                    delegaciones = delegaciones,
                                    onBackClick = { selectedTramiteId = null },
                                    onRequisitoCheckChange = { reqId, checked ->
                                        lifecycleScope.launch {
                                            repository.updateRequisitoStatus(reqId, checked)
                                            if (checked && tramite?.lastVisited == null) repository.markTramiteAsVisited(id)
                                        }
                                    },
                                    onCompleteClick = { completed ->
                                        lifecycleScope.launch { repository.updateCompletionStatus(id, completed) }
                                    },
                                    onResetClick = { lifecycleScope.launch { repository.resetTramite(id) } },
                                    onStartTracking = { lifecycleScope.launch { repository.markTramiteAsVisited(id) } },
                                    onDelegationClick = { delId ->
                                        selectedDelegationId = delId
                                        showMap = true
                                        selectedTramiteId = null
                                    },
                                    onSaveClick = { selectedTramiteId = null }
                                )
                            }
                        }
                        showMap -> {
                            BackHandler {
                                showMap = false
                                selectedDelegationId = null
                            }
                            val allDelegaciones by repository.getAllDelegaciones().collectAsState(initial = emptyList())
                            val completedCount by repository.getCompletedTramitesCount().collectAsState(initial = 0)
                            MapScreen(
                                delegaciones = allDelegaciones,
                                initialSelectedDelegationId = selectedDelegationId,
                                onHomeClick = { 
                                    showMap = false
                                    selectedDelegationId = null
                                },
                                onMyTramitesClick = {
                                    showMap = false
                                    showMyTramites = true
                                },
                                completedCount = completedCount
                            )
                        }
                        showMyTramites -> {
                            BackHandler { showMyTramites = false }
                            val myTramitesProgress by repository.getMyTramitesWithProgress().collectAsState(initial = emptyList())
                            val completedCount by repository.getCompletedTramitesCount().collectAsState(initial = 0)
                            MyTramitesScreen(
                                tramitesProgress = myTramitesProgress,
                                onTramiteClick = { selectedTramiteId = it },
                                onHomeClick = { showMyTramites = false },
                                onMapClick = { 
                                    showMyTramites = false
                                    showMap = true 
                                },
                                completedCount = completedCount
                            )
                        }
                        selectedCategory != null -> {
                            BackHandler { selectedCategory = null }
                            val category = selectedCategory!!
                            val tramites by repository.getTramitesByCategoria(category.id).collectAsState(initial = emptyList())
                            TramitesListScreen(
                                categoriaNombre = category.nombre,
                                tramites = tramites,
                                onBackClick = { selectedCategory = null },
                                onTramiteClick = { selectedTramiteId = it }
                            )
                        }
                        else -> {
                            val categorias by repository.getAllCategorias().collectAsState(initial = emptyList())
                            val quickAccess by repository.getQuickAccessTramites().collectAsState(initial = emptyList())
                            val recentTramites by repository.getRecentTramites().collectAsState(initial = emptyList())
                            val completedCount by repository.getCompletedTramitesCount().collectAsState(initial = 0)
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
                                onSyncClick = {
                                    lifecycleScope.launch {
                                        isSyncing = true
                                        val result = syncRepository.syncFeed(force = true)
                                        syncFailed = result.isFailure
                                        isSyncing = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
