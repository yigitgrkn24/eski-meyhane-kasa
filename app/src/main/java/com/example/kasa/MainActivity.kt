package com.example.kasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

// ==================== VERI MODELLERİ ====================

data class MenuItem(
    val id: String,
    val name: String,
    var price: Double,
    val category: String
)

data class OrderItem(
    val menuItem: MenuItem,
    var quantity: Int
)

data class Table(
    val id: String,
    var name: String,
    var isActive: Boolean = false,
    val orders: MutableList<OrderItem> = mutableListOf()
)

// ==================== YAZICI YÖNETİMİ ====================

class PrinterManager {
    companion object {
        private const val PRINTER_IP = "192.168.1.160"
        private const val PRINTER_PORT = 9100
        private const val ENCODING = "CP857"
        private const val ESTABLISHMENT_NAME = "ESKİ MEYHANE"

        suspend fun printReceipt(
            tableName: String,
            items: List<OrderItem>,
            isIntermediateReceipt: Boolean = false
        ) = withContext(Dispatchers.IO) {
            try {
                val socket = Socket(PRINTER_IP, PRINTER_PORT)
                val writer = OutputStreamWriter(socket.getOutputStream(), ENCODING)

                val receipt = buildReceipt(tableName, items, isIntermediateReceipt)
                writer.write(receipt)
                writer.flush()

                // ESC/POS kağıt kesme komutu
                socket.getOutputStream().write(byteArrayOf(0x1D, 0x56, 0x41, 0x00))
                socket.getOutputStream().flush()

                writer.close()
                socket.close()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        private fun buildReceipt(
            tableName: String,
            items: List<OrderItem>,
            isIntermediateReceipt: Boolean
        ): String {
            val sb = StringBuilder()
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("tr", "TR"))
            val currentTime = sdf.format(Date())

            sb.append("\n")
            sb.append("=".repeat(32)).append("\n")
            sb.append(centerText(ESTABLISHMENT_NAME, 32)).append("\n")
            sb.append("=".repeat(32)).append("\n")

            if (isIntermediateReceipt) {
                sb.append(centerText("ARA FIS", 32)).append("\n")
            } else {
                sb.append(centerText("HESAP FISI", 32)).append("\n")
            }

            sb.append("-".repeat(32)).append("\n")
            sb.append("MASA: ${padRight(tableName, 20)}\n")
            sb.append("TARIH: $currentTime\n")
            sb.append("-".repeat(32)).append("\n\n")

            var total = 0.0
            for (item in items) {
                val line = "${padRight(item.menuItem.name, 18)} " +
                        "${padLeft(item.quantity.toString(), 2)} x " +
                        "${padLeft(String.format("%.2f", item.menuItem.price), 6)} = " +
                        padLeft(String.format("%.2f", item.menuItem.price * item.quantity), 6)
                sb.append(line).append("\n")
                total += item.menuItem.price * item.quantity
            }

            sb.append("\n")
            sb.append("-".repeat(32)).append("\n")
            sb.append("TOPLAM: ").append(padLeft(String.format("%.2f", total), 21)).append("\n")
            sb.append("=".repeat(32)).append("\n\n")
            sb.append(centerText("BU BELGE ADISYON FISDIR.", 32)).append("\n")
            sb.append(centerText("MALI DEGERI BULUNMAMAKTADIR.", 32)).append("\n")
            sb.append("\n\n\n")

            return sb.toString()
        }

        private fun centerText(text: String, width: Int): String {
            val padding = (width - text.length) / 2
            return " ".repeat(maxOf(0, padding)) + text
        }

        private fun padRight(text: String, width: Int): String {
            return if (text.length >= width) text.take(width)
            else text + " ".repeat(width - text.length)
        }

        private fun padLeft(text: String, width: Int): String {
            return if (text.length >= width) text.take(width)
            else " ".repeat(width - text.length) + text
        }
    }
}

// ==================== MAIN ACTIVITY ====================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KasaAppTheme {
                KasaMainScreen()
            }
        }
    }
}

// ==================== TEMA ====================

@Composable
fun KasaAppTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFFFFC107),
        secondary = Color(0xFFE91E63),
        tertiary = Color(0xFF00BCD4),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// ==================== ANA EKRAN ====================

@Composable
fun KasaMainScreen() {
    var selectedTab by remember { mutableStateOf(0) }

    // Veritabanı state
    val menuItems = remember {
        mutableStateOf(
            listOf(
                MenuItem("1", "Hummus", 25.0, "Mezeler"),
                MenuItem("2", "Baba Ganoush", 30.0, "Mezeler"),
                MenuItem("3", "Tarama", 28.0, "Mezeler"),
                MenuItem("4", "Sigara Boregi", 20.0, "Ara Sıcaklar"),
                MenuItem("5", "Calamari Kizartma", 45.0, "Ara Sıcaklar"),
                MenuItem("6", "Manti", 35.0, "Ara Sıcaklar"),
                MenuItem("7", "Adana Kebab", 55.0, "Ana Yemekler"),
                MenuItem("8", "Urfa Kebab", 55.0, "Ana Yemekler"),
                MenuItem("9", "Kofta", 50.0, "Ana Yemekler"),
                MenuItem("10", "Ayran", 10.0, "İçecekler"),
                MenuItem("11", "Maden Suyu", 8.0, "İçecekler"),
                MenuItem("12", "Kola", 12.0, "İçecekler")
            )
        )
    }

    val tables = remember {
        mutableStateOf(
            listOf(
                Table("1", "Masa 1"),
                Table("2", "Masa 2"),
                Table("3", "Masa 3"),
                Table("4", "Masa 4"),
                Table("5", "Masa 5"),
                Table("6", "Masa 6"),
                Table("7", "VIP 1"),
                Table("8", "VIP 2")
            )
        )
    }

    var selectedTable by remember { mutableStateOf<Table?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF2A2A2A),
                modifier = Modifier.height(64.dp)
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    label = { Text("KASA", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFFC107),
                        selectedTextColor = Color(0xFFFFC107),
                        indicatorColor = Color(0xFF333333)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    label = { Text("MENÜ", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFFC107),
                        selectedTextColor = Color(0xFFFFC107),
                        indicatorColor = Color(0xFF333333)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    label = { Text("MASALAR", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFFC107),
                        selectedTextColor = Color(0xFFFFC107),
                        indicatorColor = Color(0xFF333333)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> CashierScreen(
                    tables = tables.value,
                    menuItems = menuItems.value,
                    selectedTable = selectedTable,
                    onTableSelected = {
                        selectedTable = it
                        for (table in tables.value) {
                            table.isActive = table.id == it.id
                        }
                    },
                    onPrintIntermediateReceipt = { table ->
                        scope.launch {
                            PrinterManager.printReceipt(table.name, table.orders, isIntermediateReceipt = true)
                        }
                    },
                    onPrintFinalReceipt = { table ->
                        scope.launch {
                            PrinterManager.printReceipt(table.name, table.orders, isIntermediateReceipt = false)
                            table.orders.clear()
                            table.isActive = false
                            selectedTable = null
                        }
                    },
                    onAddOrderItem = { table, menuItem ->
                        val existingOrder = table.orders.find { it.menuItem.id == menuItem.id }
                        if (existingOrder != null) {
                            existingOrder.quantity++
                        } else {
                            table.orders.add(OrderItem(menuItem, 1))
                        }
                    }
                )
                1 -> MenuManagementScreen(
                    menuItems = menuItems,
                    onAddMenuItem = { name, price, category ->
                        menuItems.value = menuItems.value + MenuItem(
                            UUID.randomUUID().toString(),
                            name,
                            price,
                            category
                        )
                    },
                    onUpdateMenuItem = { item, newPrice ->
                        menuItems.value = menuItems.value.map {
                            if (it.id == item.id) it.copy(price = newPrice) else it
                        }
                    },
                    onDeleteMenuItem = { item ->
                        menuItems.value = menuItems.value.filter { it.id != item.id }
                    }
                )
                2 -> TableManagementScreen(
                    tables = tables,
                    onAddTable = { name ->
                        tables.value = tables.value + Table(
                            UUID.randomUUID().toString(),
                            name
                        )
                    },
                    onUpdateTableName = { table, newName ->
                        tables.value = tables.value.map {
                            if (it.id == table.id) it.copy(name = newName) else it
                        }
                    },
                    onDeleteTable = { table ->
                        if (table.orders.isEmpty()) {
                            tables.value = tables.value.filter { it.id != table.id }
                            if (selectedTable?.id == table.id) {
                                selectedTable = null
                            }
                        }
                    }
                )
            }
        }
    }
}

// ==================== KASA EKRANI ====================

@Composable
fun CashierScreen(
    tables: List<Table>,
    menuItems: List<MenuItem>,
    selectedTable: Table?,
    onTableSelected: (Table) -> Unit,
    onPrintIntermediateReceipt: (Table) -> Unit,
    onPrintFinalReceipt: (Table) -> Unit,
    onAddOrderItem: (Table, MenuItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Sol Panel: Masalar
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .background(Color(0xFF1E1E1E))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "MASALAR",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFC107),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tables) { table ->
                    val backgroundColor = when {
                        table.isActive -> Color(0xFFFFD700)
                        table.orders.isNotEmpty() -> Color(0xFFEF5350)
                        else -> Color(0xFF808080)
                    }

                    Button(
                        onClick = { onTableSelected(table) },
                        modifier = Modifier
                            .height(80.dp)
                            .fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                table.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.Black
                            )
                            if (table.orders.isNotEmpty()) {
                                Text(
                                    "${String.format("%.2f", table.orders.sumOf { it.menuItem.price * it.quantity })} TL",
                                    fontSize = 12.sp,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hızlı Butonlar
            Text(
                "HIZLI SİPARİŞ",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFC107),
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            val popularItems = menuItems.filter {
                it.name in listOf("Ayran", "Kola", "Hummus", "Sigara Boregi", "Adana Kebab")
            }

            popularItems.forEach { item ->
                Button(
                    onClick = {
                        selectedTable?.let { onAddOrderItem(it, item) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = selectedTable != null
                ) {
                    Text(
                        "${item.name} (${String.format("%.2f", item.price)} TL)",
                        fontSize = 11.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Sağ Panel: Adisyon Detayları ve Menü
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .background(Color(0xFF1E1E1E))
                .padding(12.dp)
        ) {
            if (selectedTable == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Bir masa seçiniz",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            } else {
                // Adisyon Başlığı
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            selectedTable.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFC107)
                        )
                        Text(
                            "TOPLAM: ${String.format("%.2f", selectedTable.orders.sumOf { it.menuItem.price * it.quantity })} TL",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00BCD4)
                        )
                    }
                }

                // Kategori Sekmeleri
                var selectedCategory by remember { mutableStateOf("Tümü") }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf("Tümü", "Mezeler", "Ara Sıcaklar", "Ana Yemekler", "İçecekler")
                    items(categories) { category ->
                        Button(
                            onClick = { selectedCategory = category },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedCategory == category) Color(0xFFFFC107) else Color(0xFF333333)
                            ),
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedCategory == category) Color.Black else Color.White
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Ürün Listesi
                    val filteredItems = if (selectedCategory == "Tümü") {
                        menuItems
                    } else {
                        menuItems.filter { it.category == selectedCategory }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems) { item ->
                            Button(
                                onClick = { onAddOrderItem(selectedTable, item) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        item.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Text(
                                        "${String.format("%.2f", item.price)} TL",
                                        fontSize = 10.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Sağ Panel: Adisyon Ürünleri
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .background(Color(0xFF2A2A2A))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(selectedTable.orders) { orderItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF333333), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            orderItem.menuItem.name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            "${orderItem.quantity}x${String.format("%.2f", orderItem.menuItem.price)}",
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            orderItem.quantity--
                                            if (orderItem.quantity == 0) {
                                                selectedTable.orders.remove(orderItem)
                                            }
                                        },
                                        modifier = Modifier
                                            .width(30.dp)
                                            .height(30.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("-", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        }

                        // Butonlar
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onPrintIntermediateReceipt(selectedTable) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "ARA FİŞ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            Button(
                                onClick = { onPrintFinalReceipt(selectedTable) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "HESAP AL",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== MENÜ YÖNETİMİ EKRANI ====================

@Composable
fun MenuManagementScreen(
    menuItems: MutableState<List<MenuItem>>,
    onAddMenuItem: (String, Double, String) -> Unit,
    onUpdateMenuItem: (MenuItem, Double) -> Unit,
    onDeleteMenuItem: (MenuItem) -> Unit
) {
    var newItemName by remember { mutableStateOf("") }
    var newItemPrice by remember { mutableStateOf("") }
    var newItemCategory by remember { mutableStateOf("Mezeler") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text(
            "MENÜ YÖNETİMİ",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFC107),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Yeni Ürün Ekleme
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "YENİ ÜRÜN EKLE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00BCD4),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text("Ürün Adı") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                )

                OutlinedTextField(
                    value = newItemPrice,
                    onValueChange = { newItemPrice = it },
                    label = { Text("Fiyat (TL)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                )

                val categories = listOf("Mezeler", "Ara Sıcaklar", "Ana Yemekler", "İçecekler")
                var expandedCategory by remember { mutableStateOf(false) }

                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    Button(
                        onClick = { expandedCategory = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                    ) {
                        Text(newItemCategory, color = Color.White)
                    }

                    DropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    newItemCategory = category
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (newItemName.isNotEmpty() && newItemPrice.isNotEmpty()) {
                            onAddMenuItem(newItemName, newItemPrice.toDoubleOrNull() ?: 0.0, newItemCategory)
                            newItemName = ""
                            newItemPrice = ""
                            newItemCategory = "Mezeler"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("EKLE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Ürün Listesi
        Text(
            "MEVCUT ÜRÜNLER",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFC107),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(menuItems.value) { item ->
                var editingPrice by remember { mutableStateOf(item.price.toString()) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                item.category,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        OutlinedTextField(
                            value = editingPrice,
                            onValueChange = {
                                editingPrice = it
                                it.toDoubleOrNull()?.let { price ->
                                    onUpdateMenuItem(item, price)
                                }
                            },
                            modifier = Modifier
                                .width(80.dp)
                                .height(48.dp),
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                            suffix = { Text("TL", fontSize = 10.sp) }
                        )

                        Button(
                            onClick = { onDeleteMenuItem(item) },
                            modifier = Modifier
                                .width(40.dp)
                                .height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ==================== MASA YÖNETİMİ EKRANI ====================

@Composable
fun TableManagementScreen(
    tables: MutableState<List<Table>>,
    onAddTable: (String) -> Unit,
    onUpdateTableName: (Table, String) -> Unit,
    onDeleteTable: (Table) -> Unit
) {
    var newTableName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text(
            "MASA DÜZENİ",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFC107),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Yeni Masa Ekleme
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "YENİ MASA EKLE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00BCD4),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTableName,
                        onValueChange = { newTableName = it },
                        label = { Text("Masa İsmi") },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(color = Color.White)
                    )

                    Button(
                        onClick = {
                            if (newTableName.isNotEmpty()) {
                                onAddTable(newTableName)
                                newTableName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("EKLE", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Masa Listesi
        Text(
            "MEVCUT MASALAR",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFC107),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tables.value) { table ->
                var editingName by remember { mutableStateOf(table.name) }
                val hasActiveOrders = table.orders.isNotEmpty()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editingName,
                            onValueChange = {
                                editingName = it
                                onUpdateTableName(table, it)
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(color = Color.White)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (hasActiveOrders) {
                            Button(
                                onClick = { },
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF888888)),
                                enabled = false,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                            }
                        } else {
                            Button(
                                onClick = { onDeleteTable(table) },
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                            }
                        }
                    }

                    if (hasActiveOrders) {
                        Text(
                            "⚠ Aktif hesap var, silinemiyor",
                            fontSize = 10.sp,
                            color = Color(0xFFFF9800),
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
