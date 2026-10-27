package com.nictra.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nictra.data.local.entities.CategoriaEntity
import com.nictra.data.local.entities.TramiteEntity
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    categorias: List<CategoriaEntity>,
    quickAccess: List<TramiteEntity>,
    recentTramites: List<TramiteEntity>,
    searchResults: List<TramiteEntity>,
    searchQuery: String,
    lastSyncDate: String?,
    isSyncing: Boolean,
    syncFailed: Boolean,
    onSearchQueryChange: (String) -> Unit,
    completedCount: Int,
    onCategoryClick: (CategoriaEntity) -> Unit,
    onTramiteClick: (String) -> Unit,
    onMyTramitesClick: () -> Unit,
    onMapClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                    label = { Text("Inicio") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { 
                        if (completedCount > 0) {
                            BadgedBox(badge = { Badge { Text(completedCount.toString()) } }) {
                                Icon(Icons.Default.List, contentDescription = "Mis Trámites")
                            }
                        } else {
                            Icon(Icons.Default.List, contentDescription = "Mis Trámites")
                        }
                    },
                    label = { Text("Mis Trámites") },
                    selected = false,
                    onClick = onMyTramitesClick
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, contentDescription = "Mapa") },
                    label = { Text("Mapa") },
                    selected = false,
                    onClick = onMapClick
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F7FA))
        ) {
            item {
                HeaderSection(searchQuery, onSearchQueryChange)
            }

            item {
                SyncStatusBanner(lastSyncDate, isSyncing, syncFailed, onSyncClick)
            }

            if (searchQuery.isNotEmpty()) {
                item {
                    SectionTitle("Resultados para \"$searchQuery\"")
                }
                if (searchResults.isEmpty()) {
                    item {
                        Text(
                            "No se encontraron trámites con ese nombre.",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray
                        )
                    }
                } else {
                    items(searchResults) { tramite ->
                        RecentTramiteCard(tramite, onTramiteClick)
                    }
                }
            } else {
                item {
                    SectionTitle("¿Qué necesitas hacer hoy?")
                    CategoriesGrid(categorias, onCategoryClick)
                }
                
                if (quickAccess.isNotEmpty()) {
                    item {
                        SectionTitle("Accesos rápidos")
                    }
                    items(quickAccess) { tramite ->
                        RecentTramiteCard(tramite, onTramiteClick)
                    }
                }
                
                item {
                    SectionTitle("Trámites recientes")
                }
                if (recentTramites.isEmpty()) {
                    item {
                        Text(
                            "No hay trámites recientes",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    items(recentTramites) { tramite ->
                        RecentTramiteCard(tramite, onTramiteClick)
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SyncStatusBanner(
    lastSyncDate: String?, 
    isSyncing: Boolean, 
    syncFailed: Boolean, 
    onSyncClick: () -> Unit
) {
    val formattedDate = remember(lastSyncDate) {
        if (lastSyncDate != null) {
            try {
                val zdt = ZonedDateTime.parse(lastSyncDate)
                zdt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
            } catch (e: Exception) {
                lastSyncDate
            }
        } else {
            "Nunca sincronizado"
        }
    }

    val bannerColor = if (syncFailed) Color(0xFFFFF3F3) else Color.White
    val textColor = if (syncFailed) Color(0xFFD32F2F) else Color.DarkGray

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bannerColor,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (syncFailed) "Sin conexión - Usando datos locales" else "Contenido actualizado el:",
                    fontSize = 11.sp,
                    color = if (syncFailed) Color(0xFFD32F2F) else Color.Gray,
                    fontWeight = if (syncFailed) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = if (isSyncing) "Sincronizando..." else formattedDate,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            }
            
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF1A3B70)
                )
            } else {
                IconButton(onClick = onSyncClick) {
                    Icon(
                        Icons.Default.Refresh, 
                        contentDescription = "Sincronizar ahora",
                        tint = Color(0xFF1A3B70),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderSection(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Color(0xFF1A3B70))
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Base de datos local activa",
                    color = Color(0xFF4CAF50),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Servicios ciudadanos - Nicaragua",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "¡Bienvenid@!",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("🇳🇮", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar trámite: Licencia, Pasaporte...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = { 
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Borrar", tint = Color.Gray)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            modifier = Modifier.padding(end = 8.dp).size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LocationOn, 
                                    contentDescription = null, 
                                    tint = Color(0xFF1A3B70),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
        color = Color(0xFF1A3B70)
    )
}

@Composable
fun CategoriesGrid(categorias: List<CategoriaEntity>, onCategoryClick: (CategoriaEntity) -> Unit) {
    val columns = 4
    val rows = categorias.chunked(columns)

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { categoria ->
                    CategoryItem(
                        categoria = categoria,
                        modifier = Modifier.weight(1f),
                        onCategoryClick = onCategoryClick
                    )
                }
                
                // Rellenar espacios vacíos si la fila no está completa
                if (rowItems.size < columns) {
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryItem(
    categoria: CategoriaEntity,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategoriaEntity) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable { onCategoryClick(categoria) }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(categoria.icono, fontSize = 28.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = categoria.nombre,
            fontSize = 11.sp,
            maxLines = 2,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            color = Color.DarkGray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun RecentTramiteCard(tramite: TramiteEntity, onTramiteClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onTramiteClick(tramite.id) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF5F7FA), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(tramite.icono, fontSize = 24.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tramite.titulo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1A3B70)
                )
                Text(
                    text = tramite.entidad,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info, 
                        contentDescription = null, 
                        modifier = Modifier.size(14.dp), 
                        tint = Color.Gray
                    )
                    Text(" ${tramite.tiempoEstimado}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Star, 
                        contentDescription = null, 
                        modifier = Modifier.size(14.dp), 
                        tint = Color(0xFFFBC02D)
                    )
                    Text(" ${tramite.costo}", fontSize = 12.sp, color = Color.Gray)
                }
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
