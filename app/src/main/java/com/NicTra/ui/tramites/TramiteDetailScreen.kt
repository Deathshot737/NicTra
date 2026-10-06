package com.nictra.ui.tramites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nictra.data.local.entities.DelegacionEntity
import com.nictra.data.local.entities.PasoEntity
import com.nictra.data.local.entities.RequisitoEntity
import com.nictra.data.local.entities.TramiteEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TramiteDetailScreen(
    tramite: TramiteEntity,
    requisitos: List<RequisitoEntity>,
    pasos: List<PasoEntity>,
    delegaciones: List<DelegacionEntity>,
    onBackClick: () -> Unit,
    onRequisitoCheckChange: (String, Boolean) -> Unit,
    onSaveClick: () -> Unit
) {
    val checkedCount = requisitos.count { it.isChecked }
    val totalReq = requisitos.size
    val progress = if (totalReq > 0) checkedCount.toFloat() / totalReq else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A3B70))
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 16.dp,
                color = Color.White
            ) {
                Button(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4EED8),
                        contentColor = Color(0xFF2E7D32)
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar progreso offline", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A3B70))
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Column {
                        Text(tramite.entidad, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(
                            tramite.titulo,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TagItem("Nicaragua")
                            TagItem(tramite.tiempoEstimado)
                            TagItem(tramite.costo)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            
            item {
                ProgressCard(checkedCount, totalReq, progress)
            }

            item {
                SectionHeader("📝 Documentos y requisitos")
            }

            items(requisitos) { requisito ->
                RequisitoCard(requisito, onRequisitoCheckChange)
            }

            if (pasos.isNotEmpty()) {
                item {
                    SectionHeader("🛤️ Paso a paso")
                }
                items(pasos) { paso ->
                    PasoCard(paso)
                }
            }

            if (delegaciones.isNotEmpty()) {
                item {
                    Text(
                        "📍 DONDE TRAMITAR",
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                items(delegaciones) { delegacion ->
                    DelegacionCard(delegacion)
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = Color(0xFF1A3B70)
    )
}

@Composable
fun PasoCard(paso: PasoEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = Color(0xFF1A3B70)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(paso.orden.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(paso.descripcion, fontSize = 14.sp)
        }
    }
}

@Composable
fun ProgressCard(checked: Int, total: Int, progress: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mi progreso de documentos", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("$checked/$total", fontWeight = FontWeight.Bold, color = Color(0xFF1A3B70))
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = Color(0xFFFFC107),
                trackColor = Color(0xFFF1F1F1)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Toca cada documento para marcarlo como obtenido.", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun RequisitoCard(requisito: RequisitoEntity, onCheckChange: (String, Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(
                width = 1.dp,
                color = if (requisito.isChecked) Color(0xFFFFC107) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = requisito.isChecked,
                    onCheckedChange = { onCheckChange(requisito.id, it) },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFC107))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(requisito.descripcion, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    requisito.nota?.let {
                        Surface(
                            color = if (requisito.esTramitePrevio) Color(0xFFFFF9C4) else Color(0xFFF5F5F5),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = if (requisito.esTramitePrevio) Color(0xFFFBC02D) else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    "Asegúrate de llevar este documento en original y copia para evitar retrasos en ventanilla.",
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 8.dp),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun TagItem(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DelegacionCard(delegacion: DelegacionEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF5F7FA)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF1A3B70))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(delegacion.nombreOficina, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(delegacion.direccion, fontSize = 12.sp, color = Color.Gray)
            }
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Abierto",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFF2E7D32),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
