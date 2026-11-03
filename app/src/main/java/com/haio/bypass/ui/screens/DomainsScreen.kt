package com.haio.bypass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.domain.DomainStore
import com.haio.bypass.ui.theme.Connected

private const val PAGE_SIZE = 50

private enum class DomainTab(val title: String) {
    TUNNEL("از تونل"),
    DIRECT("مستقیم")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainsScreen(
    domainStore: DomainStore,
    onRefresh: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val allDomains by domainStore.domains.collectAsState()
        val excludedDomains by domainStore.excludedDomains.collectAsState()
        val customDomains by domainStore.customDomains.collectAsState()
        val domainCount by domainStore.domainCount.collectAsState()
        val lastFetchTime by domainStore.lastFetchTime.collectAsState()
        var selectedTab by remember { mutableIntStateOf(0) }
        var searchQuery by remember { mutableStateOf("") }
        var showAddDialog by remember { mutableStateOf(false) }
        var newDomain by remember { mutableStateOf("") }
        var addError by remember { mutableStateOf(false) }
        var currentPage by remember { mutableIntStateOf(0) }
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val tab = DomainTab.entries[selectedTab]

        val sourceList = if (tab == DomainTab.TUNNEL) allDomains else excludedDomains
        val filteredDomains = remember(searchQuery, sourceList) {
            if (searchQuery.isEmpty()) sourceList
            else sourceList.filter { it.contains(searchQuery, ignoreCase = true) }
        }

        val totalPages = (filteredDomains.size + PAGE_SIZE - 1) / PAGE_SIZE
        val safeTotalPages = maxOf(totalPages, 1)
        val clampedPage = currentPage.coerceIn(0, safeTotalPages - 1)
        val pagedDomains = filteredDomains.drop(clampedPage * PAGE_SIZE).take(PAGE_SIZE)

        LaunchedEffect(searchQuery, selectedTab) { currentPage = 0 }

        val lastFetchText = remember(lastFetchTime) {
            if (lastFetchTime > 0) com.haio.bypass.ui.util.JalaliDate.formatMillis(lastFetchTime)
            else "هرگز"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Connected.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = Connected,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "مدیریت دامنه‌ها",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$domainCount از تونل  •  ${excludedDomains.size} مستقیم",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "افزودن دامنه",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = onRefresh) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "به‌روزرسانی دامنه‌ها",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("جستجوی دامنه...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = searchQuery.isNotEmpty(),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "پاک کردن جستجو",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { focusManager.clearFocus() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
            ) {
                DomainTab.entries.forEachIndexed { index, item ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            val count = if (item == DomainTab.TUNNEL) allDomains.size else excludedDomains.size
                            Text(
                                text = "${item.title} ($count)",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (tab == DomainTab.TUNNEL)
                            "کلید هر دامنه را خاموش کنید تا مستقیم و بدون تونل باز شود. تغییرات پس از اتصال مجدد اعمال می‌شود."
                        else
                            "این دامنه‌ها همیشه مستقیم باز می‌شوند، حتی اگر در لیست بای‌پس باشند.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredDomains.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = when {
                                searchQuery.isNotEmpty() -> "نتیجه‌ای یافت نشد"
                                tab == DomainTab.DIRECT -> "هنوز دامنه‌ای برای اتصال مستقیم انتخاب نشده"
                                else -> "لیست دامنه‌ها خالی است"
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (tab == DomainTab.DIRECT && searchQuery.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "با دکمه + یک دامنه اضافه کنید",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(pagedDomains, key = { it }) { domain ->
                        DomainItemCard(
                            domain = domain,
                            routedThroughTunnel = tab == DomainTab.TUNNEL,
                            canDelete = customDomains.contains(domain),
                            onToggle = { throughTunnel ->
                                if (throughTunnel) domainStore.includeDomain(domain)
                                else domainStore.excludeDomain(domain)
                            },
                            onDelete = { domainStore.removeDomain(domain) }
                        )
                    }
                }
            }

            Text(
                text = "آخرین دریافت: $lastFetchText" +
                        if (filteredDomains.size != sourceList.size) "  •  ${filteredDomains.size} نتیجه" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (safeTotalPages > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                PaginationBar(
                    currentPage = clampedPage,
                    totalPages = safeTotalPages,
                    onPrevious = { if (clampedPage > 0) currentPage-- },
                    onNext = { if (clampedPage < safeTotalPages - 1) currentPage++ }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddDialog = false
                    newDomain = ""
                    addError = false
                },
                icon = {
                    Icon(
                        if (tab == DomainTab.TUNNEL) Icons.Default.Language else Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        if (tab == DomainTab.TUNNEL) "افزودن دامنه به تونل" else "افزودن دامنه مستقیم",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Column {
                        Text(
                            if (tab == DomainTab.TUNNEL)
                                "این دامنه و زیردامنه‌هایش از روتر ابری عبور می‌کنند."
                            else
                                "این دامنه و زیردامنه‌هایش هرگز از تونل عبور نمی‌کنند.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newDomain,
                            onValueChange = {
                                newDomain = it
                                addError = false
                            },
                            placeholder = { Text("example.com") },
                            singleLine = true,
                            isError = addError,
                            supportingText = if (addError) {
                                { Text("لطفاً یک دامنه معتبر وارد کنید") }
                            } else null,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = newDomain.trim()
                            if (trimmed.isNotBlank() && trimmed.contains(".")) {
                                if (tab == DomainTab.TUNNEL) {
                                    domainStore.addDomain(trimmed)
                                } else {
                                    domainStore.excludeDomain(trimmed)
                                }
                                newDomain = ""
                                addError = false
                                showAddDialog = false
                            } else {
                                addError = true
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("افزودن")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddDialog = false
                        newDomain = ""
                        addError = false
                    }) {
                        Text("انصراف")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun DomainItemCard(
    domain: String,
    routedThroughTunnel: Boolean,
    canDelete: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (routedThroughTunnel) Icons.Default.Shield else Icons.Default.Public,
                contentDescription = null,
                tint = if (routedThroughTunnel) Connected.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = domain,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (canDelete) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف دامنه",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Switch(
                checked = routedThroughTunnel,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف دامنه", fontWeight = FontWeight.SemiBold) },
            text = { Text("آیا از حذف \"$domain\" اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("انصراف")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onPrevious,
                enabled = currentPage > 0,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (currentPage > 0)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "صفحه قبل",
                    tint = if (currentPage > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "${currentPage + 1} / $totalPages",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            FilledIconButton(
                onClick = onNext,
                enabled = currentPage < totalPages - 1,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (currentPage < totalPages - 1)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "صفحه بعد",
                    tint = if (currentPage < totalPages - 1) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
