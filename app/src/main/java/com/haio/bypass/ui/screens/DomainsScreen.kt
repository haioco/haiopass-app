package com.haio.bypass.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.domain.DomainStore
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainsScreen(
    domainStore: DomainStore,
    onRefresh: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val allDomains by domainStore.domains.collectAsState()
        val domainCount by domainStore.domainCount.collectAsState()
        val lastFetchTime by domainStore.lastFetchTime.collectAsState()
        var searchQuery by remember { mutableStateOf("") }
        var showAddDialog by remember { mutableStateOf(false) }
        var newDomain by remember { mutableStateOf("") }
        var currentPage by remember { mutableIntStateOf(0) }

        val filteredDomains = remember(searchQuery, allDomains) {
            if (searchQuery.isEmpty()) allDomains
            else allDomains.filter { it.contains(searchQuery, ignoreCase = true) }
        }

        val totalPages = (filteredDomains.size + PAGE_SIZE - 1) / PAGE_SIZE
        val safeTotalPages = maxOf(totalPages, 1)
        val clampedPage = currentPage.coerceIn(0, safeTotalPages - 1)
        val pagedDomains = filteredDomains.drop(clampedPage * PAGE_SIZE).take(PAGE_SIZE)

        LaunchedEffect(searchQuery) {
            currentPage = 0
        }

        val lastFetchText = remember(lastFetchTime) {
            if (lastFetchTime > 0) {
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                sdf.format(Date(lastFetchTime))
            } else {
                "هرگز"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "دامنه‌های بای‌پس",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "آخرین دریافت: $lastFetchText",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "افزودن دامنه",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "به‌روزرسانی",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("جستجوی دامنه...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$domainCount دامنه بارگذاری شد" +
                        if (filteredDomains.size != allDomains.size)
                            " (${filteredDomains.size} مورد مطابقت)" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(pagedDomains, key = { it }) { domain ->
                    DomainItem(
                        domain = domain,
                        onDelete = { domainStore.removeDomain(domain) }
                    )
                }
            }

            if (safeTotalPages > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (clampedPage > 0) currentPage-- },
                        enabled = clampedPage > 0
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "صفحه قبل",
                            tint = if (clampedPage > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${clampedPage + 1} / $safeTotalPages",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    IconButton(
                        onClick = { if (clampedPage < safeTotalPages - 1) currentPage++ },
                        enabled = clampedPage < safeTotalPages - 1
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "صفحه بعد",
                            tint = if (clampedPage < safeTotalPages - 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddDialog = false
                    newDomain = ""
                },
                title = { Text("افزودن دامنه") },
                text = {
                    OutlinedTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newDomain.isNotBlank()) {
                                domainStore.addDomain(newDomain)
                                newDomain = ""
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("افزودن")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            newDomain = ""
                        }
                    ) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}

@Composable
private fun DomainItem(
    domain: String,
    onDelete: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = domain,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
