package com.satar.vpn

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.UUID

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: String,
    val link: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SatarApp(
                    load = { readProfiles() },
                    save = { writeProfiles(it) }
                )
            }
        }
    }

    private fun readProfiles(): List<Profile> {
        val raw = getSharedPreferences("satar_vpn", MODE_PRIVATE).getString("profiles", "[]") ?: "[]"
        val arr = org.json.JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                Profile(o.getString("id"), o.getString("name"), o.getString("protocol"), o.getString("link"))
            }.getOrNull()
        }
    }

    private fun writeProfiles(profiles: List<Profile>) {
        val arr = org.json.JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("protocol", p.protocol).put("link", p.link))
        }
        getSharedPreferences("satar_vpn", MODE_PRIVATE).edit().putString("profiles", arr.toString()).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatarApp(load: () -> List<Profile>, save: (List<Profile>) -> Unit) {
    var profiles by remember { mutableStateOf(load()) }
    var selected by remember { mutableStateOf<String?>(profiles.firstOrNull()?.id) }
    var link by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showSub by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("آماده؛ هنوز به VPN وصل نیستی") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun update(list: List<Profile>) {
        profiles = list
        save(list)
        if (selected !in list.map { it.id }) selected = list.firstOrNull()?.id
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SATAR VPN", fontWeight = FontWeight.Black)
                        Text("مدیریت کانفیگ‌ها", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { showSub = true }) { Icon(Icons.Default.Refresh, contentDescription = "Subscription") }
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "Add") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("وضعیت اتصال", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("●", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineMedium)
                        Text("قطع", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { message = "هستهٔ اتصال هنوز اضافه نشده؛ این نسخه تونل واقعی ایجاد نمی‌کند." },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("اتصال — هسته آماده نیست") }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("کانفیگ‌های من", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${profiles.size} پروفایل", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (profiles.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("هنوز کانفیگی اضافه نکردی")
                            Spacer(Modifier.height(6.dp))
                            Text("از دکمهٔ + لینک کانفیگ یا از آیکون ↻ آدرس Subscription را وارد کن.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(profiles, key = { it.id }) { p ->
                Card(
                    onClick = { selected = p.id },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected == p.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == p.id, onClick = { selected = p.id })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(p.protocol.uppercase(), style = MaterialTheme.typography.labelSmall)
                            Text(p.link.take(62), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton(onClick = { update(profiles.filterNot { it.id == p.id }) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف")
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("افزودن کانفیگ")
                }
                OutlinedButton(onClick = { showSub = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("به‌روزرسانی Subscription")
                }
                Text("امنیت: لینک‌ها در حافظهٔ محلی برنامه ذخیره می‌شوند. فعلاً رمزنگاری اختصاصی پیاده‌سازی نشده است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("افزودن کانفیگ") },
            text = {
                Column {
                    Text("لینک کامل کانفیگ را وارد کن:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = link, onValueChange = { link = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("vless://… یا vmess://…") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = parseLink(link.trim())
                    if (parsed == null) {
                        message = "لینک معتبر یا پشتیبانی‌شده نیست."
                    } else {
                        update(profiles + parsed)
                        selected = parsed.id
                        link = ""
                        showAdd = false
                        message = "پروفایل ذخیره شد؛ برای اتصال واقعی موتور sing-box لازم است."
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("لغو") } }
        )
    }

    if (showSub) {
        AlertDialog(
            onDismissRequest = { if (!busy) showSub = false },
            title = { Text("Subscription") },
            text = {
                Column {
                    Text("آدرس اشتراک HTTPS را وارد کن:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subUrl, onValueChange = { subUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://example.com/sub…") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true
                    )
                    if (busy) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { fetchSubscription(subUrl.trim()) }
                        if (result.isSuccess) {
                            val incoming = result.getOrThrow()
                            update((profiles.filterNot { old -> incoming.any { it.link == old.link } }) + incoming)
                            message = "${incoming.size} پروفایل از Subscription خوانده شد."
                            showSub = false
                        } else {
                            message = "دریافت ناموفق بود: ${result.exceptionOrNull()?.message ?: "خطای شبکه"}"
                        }
                        busy = false
                    }
                }) { Text("دریافت") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showSub = false }) { Text("بستن") } }
        )
    }
}

fun parseLink(raw: String): Profile? {
    if (raw.isBlank()) return null
    val protocol = raw.substringBefore("://", "").lowercase()
    val supported = setOf("vless", "vmess", "trojan", "ss", "shadowsocks", "hysteria", "hysteria2", "tuic", "wireguard")
    if (protocol !in supported) return null
    val name = runCatching {
        val fragment = raw.substringAfter("#", "")
        if (fragment.isNotBlank()) java.net.URLDecoder.decode(fragment, "UTF-8")
        else {
            val uri = URI(raw)
            uri.host ?: protocol.uppercase()
        }
    }.getOrDefault(protocol.uppercase())
    return Profile(name = name, protocol = protocol, link = raw)
}

suspend fun fetchSubscription(url: String): Result<List<Profile>> = withContext(Dispatchers.IO) {
    runCatching {
        require(url.startsWith("https://")) { "برای امنیت، فقط HTTPS پذیرفته می‌شود." }
        val client = OkHttpClient.Builder().followRedirects(true).build()
        val request = Request.Builder().url(url).header("User-Agent", "SatarVPN/0.1").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body?.string().orEmpty().trim()
            check(body.isNotEmpty()) { "پاسخ خالی است" }
            val decoded = runCatching {
                String(Base64.decode(body.replace("\n", "").replace("\r", ""), Base64.DEFAULT))
            }.getOrDefault(body)
            val links = decoded.lines().map { it.trim() }
                .filter { it.substringBefore("://", "").lowercase() in setOf("vless", "vmess", "trojan", "ss", "shadowsocks", "hysteria", "hysteria2", "tuic", "wireguard") }
            links.mapNotNull(::parseLink).distinctBy { it.link }
        }
    }
}
