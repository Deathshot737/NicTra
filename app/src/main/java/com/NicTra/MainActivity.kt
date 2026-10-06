package com.nictra

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.nictra.ui.tramites.TramiteDetailScreen
import com.nictra.ui.tramites.TramitesListScreen
import com.nictra.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = TramiteRepository(db.tramiteDao())

        setContent {
            var showWelcome by remember { mutableStateOf(true) }
            var selectedCategory by remember { mutableStateOf<CategoriaEntity?>(null) }
            var selectedTramiteId by remember { mutableStateOf<String?>(null) }

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
                                        }
                                    },
                                    onSaveClick = { 
                                        selectedTramiteId = null 
                                    }
                                )
                            }
                        }
                        selectedCategory != null -> {
                            val category = selectedCategory!!
                            val tramites by repository.getTramitesByCategoria(category.id).collectAsState(initial = emptyList())
                            
                            TramitesListScreen(
                                categoriaNombre = category.nombre,
                                tramites = tramites,
                                onBackClick = { selectedCategory = null },
                                onTramiteClick = { tramiteId ->
                                    lifecycleScope.launch {
                                        repository.markTramiteAsVisited(tramiteId)
                                    }
                                    selectedTramiteId = tramiteId
                                }
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
                                completedCount = completedCount,
                                onCategoryClick = { category ->
                                    selectedCategory = category
                                },
                                onTramiteClick = { tramiteId ->
                                    lifecycleScope.launch {
                                        repository.markTramiteAsVisited(tramiteId)
                                    }
                                    selectedTramiteId = tramiteId
                                },
                                onSearchClick = {
                                    // Lógica de búsqueda
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
