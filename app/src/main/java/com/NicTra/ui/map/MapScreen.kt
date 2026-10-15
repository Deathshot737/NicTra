package com.nictra.ui.map

import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.nictra.data.local.entities.DelegacionEntity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    
    // Delegación seleccionada
    var selectedDelegation by remember(delegaciones, initialSelectedDelegationId) { 
        mutableStateOf(
            delegaciones.find { it.id == initialSelectedDelegationId } 
            ?: delegaciones.firstOrNull()
        ) 
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1A3B70))
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                Text("León, Nicaragua", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Text("Ubicaciones de Instituciones", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color.White
                            )
                        }
                    },
                    divider = {}
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, 
                        text = { Row { Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("Mapa") } })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, 
                        text = { Row { Icon(Icons.Default.List, null); Spacer(Modifier.width(8.dp)); Text("Directorio") } })
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(icon = { Icon(Icons.Default.Home, null) }, label = { Text("Inicio") }, selected = false, onClick = onHomeClick)
                NavigationBarItem(icon = { BadgedBox(badge = { if(completedCount > 0) Badge { Text(completedCount.toString()) } }) { Icon(Icons.Default.List, null) } }, 
                    label = { Text("Mis Trámites") }, selected = false, onClick = onMyTramitesClick)
                NavigationBarItem(icon = { Icon(Icons.Default.Place, null) }, label = { Text("Mapa") }, selected = true, 
                    onClick = { if (selectedTab != 0) selectedTab = 0 })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // EL MAPA SIEMPRE ESTÁ PRESENTE (Evita crashes por recreación y NullPointer en Repository)
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(15.0)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mv ->
                    // Verificación de seguridad vital para osmdroid
                    if (mv.repository == null) return@AndroidView

                    try {
                        mv.overlays.clear()
                        delegaciones.forEach { delegation ->
                            val marker = Marker(mv).apply {
                                position = GeoPoint(delegation.latitud, delegation.longitud)
                                title = delegation.nombreOficina
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                setOnMarkerClickListener { m, _ ->
                                    selectedDelegation = delegation
                                    m.showInfoWindow()
                                    true
                                }
                            }
                            mv.overlays.add(marker)
                        }

                        // Animación segura sólo si estamos viendo el mapa
                        if (selectedTab == 0 && selectedDelegation != null) {
                            val point = GeoPoint(selectedDelegation!!.latitud, selectedDelegation!!.longitud)
                            mv.post {
                                mv.controller.animateTo(point, 17.0, 1000L)
                            }
                        }
                        mv.invalidate()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                onRelease = { mv ->
                    // Limpieza final sólo cuando se sale de la pantalla definitivamente
                    mv.onDetach()
                }
            )

            // CAPA SUPERIOR: Si la pestaña es Directorio (1), cubrimos el mapa con la lista
            if (selectedTab == 1) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F7FA)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(delegaciones) { delegation ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedDelegation = delegation
                                    selectedTab = 0 // Al seleccionar, volvemos a la vista de mapa
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedDelegation?.id == delegation.id) Color(0xFFE3F2FD) else Color.White
                                ),
                                border = if (selectedDelegation?.id == delegation.id) BorderStroke(1.dp, Color(0xFF1A3B70)) else null
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Place, null, tint = Color(0xFF1A3B70))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(delegation.nombreOficina, fontWeight = FontWeight.Bold)
                                        Text(delegation.direccion, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tarjeta informativa flotante (sólo sobre el mapa)
                selectedDelegation?.let { delegation ->
                    Card(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(delegation.nombreOficina, fontWeight = FontWeight.Bold, color = Color(0xFF1A3B70))
                            Text(delegation.direccion, fontSize = 12.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}
