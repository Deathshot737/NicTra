package com.nictra.ui.tramites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nictra.data.local.entities.TramiteProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTramitesScreen(
    tramitesProgress: List<TramiteProgress>,
    onTramiteClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    onMapClick: () -> Unit,
    completedCount: Int,
    onMenuClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Todos", "Pendientes", "En proceso", "Completos")

    val filteredTramites = when (selectedTab) {
        1 -> tramitesProgress.filter { it.checkedRequisitos == 0 && !it.tramite.isCompleted }
        2 -> tramitesProgress.filter { it.checkedRequisitos > 0 && !it.tramite.isCompleted }
        3 -> tramitesProgress.filter { it.tramite.isCompleted }
        else -> tramitesProgress
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A3B70))
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mis Trámites",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menú", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        count = tramitesProgress.size,
                        label = "Gestiones",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        count = tramitesProgress.count { it.checkedRequisitos > 0 && !it.tramite.isCompleted },
                        label = "En proceso",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        count = completedCount,
                        label = "Completos",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                val navBarColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1A3B70),
                    indicatorColor = Color(0xFF1A3B70).copy(alpha = 0.1f)
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
                        if (completedCount > 0) {
                            BadgedBox(badge = { Badge { Text(completedCount.toString()) } }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Mis Trámites")
                            }
                        } else {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Mis Trámites")
                        }
                    },
                    label = { Text("Mis Trámites") },
                    selected = true,
                    onClick = { },
                    colors = navBarColors
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, contentDescription = "Mapa") },
                    label = { Text("Mapa") },
                    selected = false,
                    onClick = onMapClick,
                    colors = navBarColors
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F7FA))
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF1A3B70),
                edgePadding = 16.dp,
                divider = {},
                indicator = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        modifier = Modifier
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0xFF1A3B70) else Color.White)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        text = {
                            Text(
                                title,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (filteredTramites.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay trámites en esta sección", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTramites) { item ->
                        MyTramiteCard(item, onTramiteClick)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun MyTramiteCard(item: TramiteProgress, onClick: (String) -> Unit) {
    val isCompleted = item.tramite.isCompleted
    val statusText = when {
        isCompleted -> "Completado"
        item.checkedRequisitos > 0 -> "En proceso"
        else -> "Pendiente"
    }
    val statusColor = when {
        isCompleted -> Color(0xFF2E7D32)
        item.checkedRequisitos > 0 -> Color(0xFF1976D2)
        else -> Color(0xFFFBC02D)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.tramite.id) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF5F7FA)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.tramite.icono, fontSize = 24.sp)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.tramite.titulo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1A3B70)
                    )
                    // Eliminado el hardcoded "Hoy 08:32" si no se usa
                }
                Text(
                    item.tramite.entidad,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            statusText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (!isCompleted) {
                        val missing = item.totalRequisitos - item.checkedRequisitos
                        Text(
                            text = if (missing == 0) "¡Documentos listos!" else "$missing faltan",
                            color = if (missing == 0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            "¡Finalizado!",
                            color = Color(0xFF2E7D32),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
