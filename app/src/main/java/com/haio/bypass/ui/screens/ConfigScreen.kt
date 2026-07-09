package com.haio.bypass.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.config.TrojanUrlParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    configManager: ConfigManager,
    onSave: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val config by configManager.config.collectAsState()
        var trojanUrl by remember { mutableStateOf(config.trojanUrl) }
        var showError by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }
        val clipboardManager = LocalClipboardManager.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "تنظیمات تروجان",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = trojanUrl,
                onValueChange = {
                    trojanUrl = it
                    showError = false
                    saved = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("آدرس تروجان") },
                placeholder = { Text("trojan://password@server:443?sni=server.com") },
                isError = showError,
                supportingText = if (showError) {
                    { Text("آدرس تروجان نامعتبر است") }
                } else null,
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.toString() ?: ""
                        if (clip.isNotEmpty()) {
                            trojanUrl = clip
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("جای‌گذاری", fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        val parsed = TrojanUrlParser.parse(trojanUrl)
                        if (parsed != null) {
                            configManager.updateConfig {
                                it.copy(
                                    trojanUrl = trojanUrl,
                                    trojanConfig = parsed
                                )
                            }
                            saved = true
                            showError = false
                            onSave()
                        } else {
                            showError = true
                            saved = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (saved) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره شد", fontSize = 14.sp)
                    } else {
                        Text("ذخیره", fontSize = 14.sp)
                    }
                }
            }

            if (config.trojanConfig != null) {
                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "کانفیگ فعلی",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("سرور", config.trojanConfig!!.server)
                        InfoRow("پورت", config.trojanConfig!!.port.toString())
                        InfoRow("SNI", config.trojanConfig!!.sni)
                        InfoRow("رمز", "••••••••")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
