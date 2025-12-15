package com.example.travelupa

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.travelupa.ui.theme.TravelupaTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.FirebaseApp
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.unit.DpOffset
import android.util.Log
import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign


// --- SEALEAD CLASS NAVIGASI (DITAMBAH GREETING) ---
sealed class Screen(val route: String) {
    object Greeting : Screen("greeting") // Rute baru
    object Login : Screen("login")
    object Register : Screen("register")
    object RekomendasiTempat : Screen("rekomendasi_tempat")
}

// --- Data Class ---
data class TempatWisata(
    val nama: String = "",
    val deskripsi: String = "",
    val gambarUriString: String? = null,
    val gambarResId: Int? = null
)

// --- DATA STATIS DARI BAB 3 ---
val daftarTempatWisataStatis = listOf(
    TempatWisata(
        "Tumpak Sewu",
        "Air terjun tercantik di Jawa Timur.",
        gambarResId = R.drawable.tumpak_sewu
    ),
    TempatWisata(
        "Gunung Bromo",
        "Matahari terbitnya bagus banget.",
        gambarResId = R.drawable.gunung_bromo
    )
)

// --- Composable: Greeting Screen (BARU) ---
@Composable
fun GreetingScreen(
    onStart: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Selamat Datang di Travelupa!",
                style = MaterialTheme.typography.h4,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Solusi buat kamu yang lupa kemana-mana",
                style = MaterialTheme.typography.h6
            )
        }
        Button(
            onClick = onStart, // Memicu navigasi ke Login
            modifier = Modifier
                .width(360.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Mulai")
        }
    }
}


// --- Composable: App Navigation (UPDATE startDestination dan Greeting) ---
@Composable
fun AppNavigation(currentUser: FirebaseUser?) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (currentUser != null) Screen.RekomendasiTempat.route else Screen.Greeting.route // Perubahan di sini
    ) {
        // Rute: Greeting Screen
        composable(Screen.Greeting.route) {
            GreetingScreen(
                onStart = {
                    navController.navigate(Screen.Login.route) {
                        // Hapus Greeting dari back stack
                        popUpTo(Screen.Greeting.route) { inclusive = true }
                    }
                }
            )
        }

        // Rute: Login Screen
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.RekomendasiTempat.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        // Rute: Register Screen
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.RekomendasiTempat.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // Rute: Rekomendasi Tempat Screen
        composable(Screen.RekomendasiTempat.route) {
            RekomendasiTempatScreen(
                onBackToLogin = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Login.route) {
                        // Perubahan: Kembali ke Login, bukan Greeting (Bab 7 module menyarankan kembali ke Greeting)
                        // Mengikuti flow umum:
                        // navController.navigate(Screen.Greeting.route) {
                        // popUpTo(Screen.RekomendasiTempat.route) { inclusive = true }
                        // }

                        // Mengikuti flow kode Anda sebelumnya (kembali ke Login)
                        popUpTo(Screen.RekomendasiTempat.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser

        setContent {
            TravelupaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    AppNavigation(currentUser)
                }
            }
        }
    }
}

// --- Composable: Register Screen (Tidak Berubah) ---
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Daftar Akun Baru",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(imageVector = image, contentDescription = if (isPasswordVisible) "Hide password" else "Show password")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank() || password.length < 6) {
                    errorMessage = "Email dan password wajib diisi (min 6 karakter)."
                    return@Button
                }
                isLoading = true
                errorMessage = null
                coroutineScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await()
                        }
                        isLoading = false
                        onRegisterSuccess()
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = "Registration failed: ${e.localizedMessage}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            } else {
                Text("Daftar")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBackToLogin) {
            Text("Sudah punya akun? Login di sini.")
        }
        errorMessage?.let {
            Text(
                text = it,
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// --- Composable: Login Screen (Tidak Berubah) ---
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(imageVector = image, contentDescription = if (isPasswordVisible) "Hide password" else "Show password")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter email and password"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                coroutineScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
                        }
                        isLoading = false
                        onLoginSuccess()
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = "Login failed: ${e.localizedMessage}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            } else {
                Text("Login")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onNavigateToRegister) {
            Text("Belum punya akun? Daftar sekarang!")
        }
        errorMessage?.let {
            Text(
                text = it,
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// --- Composable: Rekomendasi Tempat Screen ---
@Composable
fun RekomendasiTempatScreen(
    onBackToLogin: () -> Unit
) {
    var daftarTempatWisataFirestore by remember { mutableStateOf(listOf<TempatWisata>()) }
    var showTambahDialog by remember { mutableStateOf(false) }

    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    val daftarTempatWisataGabungan = remember(daftarTempatWisataFirestore) {
        val firestoreNames = daftarTempatWisataFirestore.map { it.nama }.toSet()
        val uniqueStatis = daftarTempatWisataStatis.filter { it.nama !in firestoreNames }

        uniqueStatis + daftarTempatWisataFirestore
    }

    val fetchData: () -> Unit = {
        firestore.collection("tempat_wisata")
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { document ->
                    document.toObject(TempatWisata::class.java)
                }
                daftarTempatWisataFirestore = list.toList()
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error fetching documents: ", exception)
            }
    }

    LaunchedEffect(Unit) {
        fetchData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rekomendasi Tempat Wisata") },
                actions = {
                    Button(onClick = onBackToLogin) {
                        Text("Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTambahDialog = true },
                backgroundColor = MaterialTheme.colors.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah Tempat Wisata")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LazyColumn {
                items(daftarTempatWisataGabungan, key = { it.nama }) { tempat ->
                    TempatItemEditable(
                        tempat = tempat,
                        onDelete = {
                            fetchData()
                        }
                    )
                }
            }
        }

        if (showTambahDialog) {
            TambahTempatWisataDialog(
                firestore = firestore,
                context = context,
                onDismiss = { showTambahDialog = false },
                onTambah = { nama, deskripsi, gambarUriString ->
                    fetchData()
                    showTambahDialog = false
                }
            )
        }
    }
}

// --- Composable: Item Daftar yang Dapat Diedit/Dihapus ---
@Composable
fun TempatItemEditable(
    tempat: TempatWisata,
    onDelete: () -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    var expanded by remember { mutableStateOf(false) }
    val defaultImage = R.drawable.default_image

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Image(
                painter = tempat.gambarUriString?.let { uriString ->
                    rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(Uri.parse(uriString))
                            .build()
                    )
                } ?: tempat.gambarResId?.let {
                    painterResource(id = it)
                } ?: painterResource(id = defaultImage),
                contentDescription = tempat.nama,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(
                        text = tempat.nama,
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.padding(bottom = 8.dp, top = 12.dp)
                    )
                    Text(
                        text = tempat.deskripsi,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    offset = DpOffset(250.dp, 0.dp)
                ) {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        // Hanya hapus dari Firestore jika item BUKAN data statis (yang tidak punya URI String)
                        if (tempat.gambarUriString != null) {
                            firestore.collection("tempat_wisata").document(tempat.nama)
                                .delete()
                                .addOnSuccessListener {
                                    onDelete()
                                }
                                .addOnFailureListener { e ->
                                    Log.w("TempatItemEditable", "Error deleting document", e)
                                }
                        } else {
                            // Untuk data statis (ResId), cukup refresh untuk menghilangkannya dari tampilan sementara
                            onDelete()
                        }
                    }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

// --- Composable: Dialog Tambah Tempat Wisata ---
@Composable
fun TambahTempatWisataDialog(
    firestore: FirebaseFirestore,
    context: Context,
    onDismiss: () -> Unit,
    onTambah: (String, String, String?) -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var gambarUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val gambarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        gambarUri = uri
    }

    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Tempat Wisata Baru") },
        text = {
            Column {
                TextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Tempat") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = deskripsi,
                    onValueChange = { deskripsi = it },
                    label = { Text("Deskripsi") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                )
                Spacer(modifier = Modifier.height(8.dp))
                gambarUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(model = uri),
                        contentDescription = "Gambar yang dipilih",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { gambarLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                ) {
                    Text("Pilih Gambar")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nama.isNotBlank() && deskripsi.isNotBlank() && gambarUri != null) {
                        isUploading = true
                        val tempatWisata = TempatWisata(nama, deskripsi, gambarUriString = gambarUri.toString())

                        coroutineScope.launch {
                            try {
                                firestore.collection("tempat_wisata").document(nama)
                                    .set(tempatWisata)
                                    .await()

                                isUploading = false
                                onTambah(nama, deskripsi, gambarUri.toString())
                            } catch (e: Exception) {
                                isUploading = false
                                Log.e("Firestore", "Error adding document", e)
                            }
                        }
                    }
                },
                enabled = !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                } else {
                    Text("Tambah")
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface),
                enabled = !isUploading
            ) {
                Text("Batal")
            }
        }
    )
}