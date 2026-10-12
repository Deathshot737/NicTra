package com.nictra.ui.map

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.nictra.data.local.entities.DelegacionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    delegaciones: List<DelegacionEntity>,
    initialSelectedDelegationId: String? = null,
    onHomeClick: () -> Unit,
    onMyTramitesClick: () -> Unit,
    completedCount: Int
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    // Estado de la delegación seleccionada
    var selectedDelegation by remember(delegaciones, initialSelectedDelegationId) { 
        mutableStateOf(
            delegaciones.find { it.id == initialSelectedDelegationId } 
            ?: delegaciones.firstOrNull()
        ) 
    }

    // Centrar en León, Nicaragua por defecto
    val leonCenter = LatLng(12.4350, -86.8790)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(leonCenter, 15f)
    }

    // Efecto para "volar" a la delegación seleccionada
    LaunchedEffect(selectedDelegation) {
        selectedDelegation?.let {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(LatLng(it.latitud, it.longitud), 17f),
                durationMs = 1000
            )
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A3B70))
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
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color.White
                        )
                    },
                    divider = {},
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Mapa")
                        }}
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.List, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Directorio")
                        }}
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Inicio") },
                    selected = false,
                    onClick = onHomeClick
                )
                NavigationBarItem(
                    icon = { 
                        BadgedBox(badge = { if(completedCount > 0) Badge { Text(completedCount.toString()) } }) {
                            Icon(Icons.Default.List, null)
                        }
                    },
                    label = { Text("Mis Trámites") },
                    selected = false,
                    onClick = onMyTramitesClick
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, null) },
                    label = { Text("Mapa") },
                    selected = true,
                    onClick = { }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                // GOOGLE MAPS REAL 🌍
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)
                ) {
                    delegaciones.forEach { delegation ->
                        Marker(
                            state = MarkerState(position = LatLng(delegation.latitud, delegation.longitud)),
                            title = delegation.nombreOficina,
                            onClick = {
                                selectedDelegation = delegation
                                false 
                            }
                        )
                    }
                }

                // Tarjeta inferior (Exacta al Figma)
                selectedDelegation?.let { delegation ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(delegation.nombreOficina, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A3B70))
                                    Text(delegation.departamento, fontSize = 12.sp, color = Color.Gray)
                                }
                                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        text = "Abierto",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = Color(0xFF2E7D32),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.5f))
                            
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), Color(0xFFD32F2F))
                                Spacer(Modifier.width(12.dp))
                                Text(delegation.direccion, fontSize = 12.sp, color = Color.DarkGray)
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, null, Modifier.size(16.dp), Color.Gray)
                                Spacer(Modifier.width(12.dp))
                                Text("+505 2311-4672", fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }
            } else {
                // Vista de Directorio
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA)),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(delegaciones) { delegation ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { 
                                selectedDelegation = delegation
                                selectedTab = 0 
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedDelegation?.id == delegation.id) Color(0xFFE3F2FD) else Color.White),
                            border = if (selectedDelegation?.id == delegation.id) BorderStroke(1.dp, Color(0xFF1A3B70)) else null
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, null, tint = Color(0xFF1A3B70))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(delegation.nombreOficina, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(delegation.direccion, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
