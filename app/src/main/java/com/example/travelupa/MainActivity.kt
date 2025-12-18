package com.example.travelupa

import ImageEntity
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.travelupa.ui.theme.TravelupaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

// --- Model & Database ---
data class TempatWisata(
    val nama: String = "",
    val deskripsi: String = "",
    val gambarUriString: String? = null,
    val gambarResId: Int? = null
)

val daftarTempatWisataStatis = listOf(
    TempatWisata("Tumpak Sewu", "Air terjun tercantik di Jawa Timur.", gambarResId = R.drawable.tumpak_sewu),
    TempatWisata("Gunung Bromo", "Matahari terbitnya bagus banget.", gambarResId = R.drawable.gunung_bromo)
)

// --- Helper Functions Room & Storage (Bab 8) ---
fun saveImageLocally(context: Context, uri: Uri): String {
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "image_${System.currentTimeMillis()}.jpg")
        inputStream?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    } catch (e: Exception) {
        Log.e("ImageSave", "Error saving image", e)
        throw e
    }
}

fun uploadImageToFirestore(
    firestore: FirebaseFirestore,
    context: Context,
    imageUri: Uri,
    tempatWisata: TempatWisata,
    onSuccess: (TempatWisata) -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = Room.databaseBuilder(context, AppDatabase::class.java, "travelupa-database").build()
    val imageDao = db.imageDao()
    val localPath = saveImageLocally(context, imageUri)

    CoroutineScope(Dispatchers.IO).launch {
        try {
            imageDao.insert(ImageEntity(localPath = localPath))
            val updatedTempatWisata = tempatWisata.copy(gambarUriString = localPath)
            firestore.collection("tempat_wisata")
                .document(updatedTempatWisata.nama)
                .set(updatedTempatWisata)
                .await()

            withContext(Dispatchers.Main) { onSuccess(updatedTempatWisata) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onFailure(e) }
        }
    }
}

// --- CameraView Component (Bab 9) ---
@Composable
fun CameraView(
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // PERBAIKAN DI SINI:
    LaunchedEffect(Unit) {
        // Kita harus mengambil cameraProvider dari ListenenableFuture
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get() // Mengambil provider yang sudah siap
            val preview = androidx.camera.core.Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            try {
                cameraProvider.unbindAll() // Sekarang unbindAll akan terbaca
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                ) // Sekarang bindToLifecycle akan terbaca
            } catch (e: Exception) {
                Log.e("CameraView", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
        Button(
            modifier = Modifier.padding(bottom = 32.dp),
            onClick = {
                val file = File(context.filesDir, "cam_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onImageCaptured(Uri.fromFile(file))
                        }
                        override fun onError(exception: ImageCaptureException) {
                            onError(exception)
                        }
                    }
                )
            }
        ) {
            Text("Ambil Foto")
        }
    }
}

// --- Navigation ---
sealed class Screen(val route: String) {
    object Greeting : Screen("greeting")
    object Login : Screen("login")
    object Register : Screen("register")
    object RekomendasiTempat : Screen("rekomendasi")
}

@Composable
fun AppNavigation(currentUser: FirebaseUser?) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (currentUser != null) Screen.RekomendasiTempat.route else Screen.Greeting.route
    ) {
        composable(Screen.Greeting.route) { GreetingScreen { navController.navigate(Screen.Login.route) { popUpTo(Screen.Greeting.route) { inclusive = true } } } }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Screen.RekomendasiTempat.route) { popUpTo(0) } },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = { navController.navigate(Screen.RekomendasiTempat.route) { popUpTo(0) } },
                onBackToLogin = { navController.popBackStack() }
            )
        }
        composable(Screen.RekomendasiTempat.route) {
            RekomendasiTempatScreen(
                onBackToLogin = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Login.route) { popUpTo(Screen.RekomendasiTempat.route) { inclusive = true } }
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
        val user = FirebaseAuth.getInstance().currentUser
        setContent { TravelupaTheme { Surface(color = Color.White) { AppNavigation(user) } } }
    }
}

// --- Screens ---
@Composable
fun GreetingScreen(onStart: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Text("Selamat Datang di Travelupa!", style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text("Solusi buat kamu yang lupa kemana-mana", style = MaterialTheme.typography.h6)
        }
        Button(onClick = onStart, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).width(360.dp)) {
            Text("Mulai")
        }
    }
}

@Composable
fun RekomendasiTempatScreen(onBackToLogin: () -> Unit) {
    var firestoreData by remember { mutableStateOf(listOf<TempatWisata>()) }
    var showTambahDialog by remember { mutableStateOf(false) }
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    val combinedList = remember(firestoreData) {
        val firestoreNames = firestoreData.map { it.nama }.toSet()
        daftarTempatWisataStatis.filter { it.nama !in firestoreNames } + firestoreData
    }

    val fetch = {
        firestore.collection("tempat_wisata").get().addOnSuccessListener { result ->
            firestoreData = result.documents.mapNotNull { it.toObject(TempatWisata::class.java) }
        }
    }
    LaunchedEffect(Unit) { fetch() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Rekomendasi Wisata") }, actions = { Button(onClick = onBackToLogin) { Text("Logout") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { showTambahDialog = true }) { Icon(Icons.Default.Add, "") } }
    ) { p ->
        LazyColumn(Modifier.padding(p).padding(16.dp)) {
            items(combinedList, key = { it.nama }) { tempat ->
                TempatItemEditable(tempat) { fetch() }
            }
        }
        if (showTambahDialog) {
            TambahTempatWisataDialog(firestore, context, { showTambahDialog = false }, { fetch() })
        }
    }
}

@Composable
fun TambahTempatWisataDialog(firestore: FirebaseFirestore, context: Context, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    var nama by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) showCamera = true }

    if (showCamera) {
        Dialog(onDismissRequest = { showCamera = false }) {
            CameraView(onImageCaptured = { uri = it; showCamera = false }, onError = { showCamera = false })
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Tambah Tempat Wisata") },
            text = {
                Column {
                    TextField(nama, { nama = it }, label = { Text("Nama") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    TextField(desc, { desc = it }, label = { Text("Deskripsi") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    uri?.let { Image(rememberAsyncImagePainter(it), "", Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Galeri") }
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showCamera = true
                            else permissionLauncher.launch(Manifest.permission.CAMERA)
                        }) { Text("Kamera") }
                    }
                }
            },
            confirmButton = {
                Button(enabled = !isUploading, onClick = {
                    if (nama.isNotBlank() && uri != null) {
                        isUploading = true
                        uploadImageToFirestore(firestore, context, uri!!, TempatWisata(nama, desc),
                            onSuccess = { isUploading = false; onRefresh(); onDismiss() },
                            onFailure = { isUploading = false; Log.e("Upload", "Failed", it) })
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
        )
    }
}

@Composable
fun TempatItemEditable(tempat: TempatWisata, onUpdate: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val firestore = FirebaseFirestore.getInstance()
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), elevation = 4.dp) {
        Column(Modifier.padding(16.dp)) {
            val painter = when {
                tempat.gambarUriString != null -> rememberAsyncImagePainter(Uri.fromFile(File(tempat.gambarUriString)))
                tempat.gambarResId != null -> painterResource(tempat.gambarResId)
                else -> painterResource(R.drawable.default_image)
            }
            Image(painter, "", Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.align(Alignment.CenterStart)) {
                    Text(tempat.nama, style = MaterialTheme.typography.h6)
                    Text(tempat.deskripsi, style = MaterialTheme.typography.body2)
                }
                IconButton(onClick = { expanded = true }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.MoreVert, "") }
                DropdownMenu(expanded, { expanded = false }, offset = DpOffset(250.dp, 0.dp)) {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        if (tempat.gambarUriString != null) {
                            firestore.collection("tempat_wisata").document(tempat.nama).delete().addOnSuccessListener { onUpdate() }
                        } else onUpdate()
                    }) { Text("Delete") }
                }
            }
        }
    }
}

// --- Login & Register ---
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, "")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(enabled = !isLoading, onClick = {
            isLoading = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await() }
                    onLoginSuccess()
                } catch (e: Exception) { errorMessage = e.localizedMessage }
                finally { isLoading = false }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Text("Login")
        }
        TextButton(onClick = onNavigateToRegister) { Text("Belum punya akun? Daftar sekarang!") }
        errorMessage?.let { Text(it, color = Color.Red) }
    }
}

@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit, onBackToLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Daftar Akun Baru", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(enabled = !isLoading, onClick = {
            isLoading = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await() }
                    onRegisterSuccess()
                } catch (e: Exception) { errorMessage = e.localizedMessage }
                finally { isLoading = false }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Text("Daftar")
        }
        TextButton(onClick = onBackToLogin) { Text("Sudah punya akun? Login di sini.") }
        errorMessage?.let { Text(it, color = Color.Red) }
    }
}