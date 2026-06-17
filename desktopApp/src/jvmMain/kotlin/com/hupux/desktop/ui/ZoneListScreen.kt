package com.hupux.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.Zone
import com.hupux.data.model.ZoneCategory
import com.hupux.data.repository.ZoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ZoneListScreen(repo: ZoneRepository, onZoneClick: (Zone) -> Unit) {
    var categories by remember { mutableStateOf<List<ZoneCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            categories = withContext(Dispatchers.IO) { repo.getZoneList() }
        } catch (_: Exception) {}
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                categories.forEach { cat ->
                    item {
                        Text(
                            cat.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(cat.zones) { zone ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onZoneClick(zone) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = zone.topicLogo,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(zone.topicName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (zone.count.isNotEmpty()) {
                                    Text(zone.count, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
