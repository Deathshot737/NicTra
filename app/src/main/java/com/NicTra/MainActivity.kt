package com.nictra

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nictra.data.local.AppDatabase
import com.nictra.data.local.entities.CategoriaEntity
import com.nictra.data.local.entities.TramiteEntity
import com.nictra.data.repository.TramiteRepository
import com.nictra.ui.home.HomeScreen
import com.nictra.ui.map.MapScreen
import com.nictra.ui.tramites.MyTramitesScreen
import com.nictra.ui.tramites.TramiteDetailScreen
import com.nictra.ui.tramites.TramitesListScreen
import com.nictra.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicialización obligatoria de OpenStreetMap
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        val db = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = TramiteRepository(db.tramiteDao())

        setContent {
            var showWelcome by remember { mutableStateOf(true) }
            var selectedCategory by remember { mutableStateOf<CategoriaEntity?>(null) }
            var selectedTramiteId by remember { mutableStateOf<String?>(null) }
            var showMyTramites by remember { mutableStateOf(false) }
            var showMap by remember { mutableStateOf(false) }
            var selectedDelegationId by remember { mutableStateOf<String?>(null) }
            
            var searchQuery by remember { mutableStateOf("") }
            val searchResults by repository.searchTramites(searchQuery).collectAsState(initial = emptyList())

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        showWelcome -> {
                            WelcomeScreen(onNavigateToHome = { showWelcome = false })
                        }
                        selectedTramiteId != null -> {
                            BackHandler { selectedTramiteId = null }

                            val id = selectedTramiteId!!
                            val tramite by produceState<TramiteEntity?>(initialValue = null, id) {
                                value = repository.getTramiteById(id)
                            }
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
                                            if (checked && tramite?.lastVisited == null) {
                                                repository.markTramiteAsVisited(id)
                                            }
                                        }
                                    },
                                    onCompleteClick = { completed ->
                                        lifecycleScope.launch {
                                            repository.updateCompletionStatus(id, completed)
                                        }
                                    },
                                    onResetClick = {
                                        lifecycleScope.launch { repository.resetTramite(id) }
                                    },
                                    onStartTracking = {
                                        lifecycleScope.launch { repository.markTramiteAsVisited(id) }
                                    },
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
                                onSearchQueryChange = { searchQuery = it },
                                completedCount = completedCount,
                                onCategoryClick = { selectedCategory = it },
                                onTramiteClick = { selectedTramiteId = it },
                                onMyTramitesClick = { showMyTramites = true },
                                onMapClick = { showMap = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
