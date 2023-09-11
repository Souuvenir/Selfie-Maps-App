package com.example.evaluacion3_busnego_n

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.twotone.Face
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.evaluacion3_busnego_n.ui.theme.Evaluacion3_Busnego_NTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

class MissingPermissionsException(message:String):Exception(message)

class AppVm: ViewModel(){

    val currentScreen             = mutableStateOf(Screen.FORM)
    val picture                   = mutableStateOf<Uri?>(null)
    var cameraAccessOK:() -> Unit = {}

    val latitude                    = mutableStateOf(0.0)
    val longitude                   = mutableStateOf(0.0)
    var locationAccessOK:() -> Unit = {}
}

enum class Screen{
    FORM,
    CAMERA
}


class MainActivity : ComponentActivity() {
    val appVm: AppVm by viewModels()

    lateinit var cameraController: LifecycleCameraController

    val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it[android.Manifest.permission.CAMERA] ?: false) {
            appVm.cameraAccessOK()
        }
    }

    val permissionLauncherM = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (
            (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false) or
            (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false)
        ) {
            appVm.locationAccessOK()
        } else {
            Log.v("permissionLauncher callback", "Permission denied by user")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        setContent {
            MainScreenUi(permissionLauncher, cameraController, permissionLauncherM)
        }
    }

    @Composable
    fun MainScreenUi(
        permissionLauncher: ActivityResultLauncher<Array<String>>,
        cameraController: LifecycleCameraController,
        permissionLauncherM: ActivityResultLauncher<Array<String>>
    ) {

        val appVm: AppVm = viewModel()

        when (appVm.currentScreen.value) {
            Screen.FORM -> {
                ScreenFormUI(appVm, permissionLauncherM)
            }

            Screen.CAMERA -> {
                CameraScreenUI(permissionLauncher, cameraController, appVm)
            }
        }
    }

    fun GetLocations(context: Context, onSuccess: (location: Location) -> Unit) {
        try {

            val service = LocationServices.getFusedLocationProviderClient(context)
            val tarea = service.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            )
            tarea.addOnSuccessListener {
                onSuccess(it)
            }
        } catch (se: SecurityException) {
            throw MissingPermissionsException("Permission Denied By User")
        }

    }


@Composable
fun CameraScreenUI(
    permissionLauncher: ActivityResultLauncher<Array<String>>,
    cameraController: LifecycleCameraController,
    appVm: AppVm
) {
    val context = LocalContext.current
    permissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = {
                takePicture(
                    cameraController,
                    createPrivateImageFile(context),
                    context
                ) {
                    appVm.picture.value = it
                    appVm.currentScreen.value = Screen.FORM
                }
            }
        ) {
            Icon(
                Icons.TwoTone.Face,
                contentDescription = "Take Selfie",
                tint = Color.Blue,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun ScreenFormUI(appVm: AppVm, permissionLauncherM: ActivityResultLauncher<Array<String>>) {
    val context = LocalContext.current

    var fullScreenPicture by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Daily Selfie App",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    ),
                    modifier = Modifier.padding(16.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .fillMaxHeight(if (fullScreenPicture) 1f else 0.3f)
                        .clickable {
                            fullScreenPicture = !fullScreenPicture
                        }
                ) {
                    appVm.picture.value?.let { uri2ImageBitmap(it, context) }
                        ?.let { BitmapPainter(it) }?.let {
                            Image(
                                painter = it,
                                contentDescription = "Selfie From Camera",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                }
                Spacer(Modifier.height(15.dp))
                Button(
                    onClick = {
                        appVm.currentScreen.value = Screen.CAMERA
                    }
                ) {
                    Text(text = "Take Selfie")
                }
                if (appVm.picture.value != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                GetLocations(context) {
                                    appVm.latitude.value = it.latitude
                                    appVm.longitude.value = it.longitude
                                }
                            }
                            appVm.locationAccessOK = {
                                GetLocations(context) {
                                    appVm.latitude.value = it.latitude
                                    appVm.longitude.value = it.longitude
                                }
                            }
                            permissionLauncherM.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            Text(
                                text = "Selfie Location",
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(16.dp)
                            )
                            Spacer(Modifier.height(50.dp))
                            AndroidView(
                                factory = {
                                    MapView(it).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        org.osmdroid.config.Configuration.
                                        getInstance().userAgentValue =
                                            context.packageName
                                        controller.setZoom(15.0)
                                    }
                                }, update = {
                                    it.overlays.removeIf { true }
                                    it.invalidate()

                                    val geoPoint = GeoPoint(appVm.latitude.value,
                                    appVm.longitude.value)
                                    it.controller.animateTo(geoPoint)

                                    val mark = Marker(it)
                                    mark.position = geoPoint
                                    mark.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    it.overlays.add(mark)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun uri2ImageBitmap(uri: Uri, context: Context) = BitmapFactory.decodeStream(
    context.contentResolver.openInputStream(uri)
).asImageBitmap()

fun createNameByDate(): String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0, 14)

fun createPrivateImageFile(context: Context): File = File(
    context.getExternalFilesDir(Environment.DIRECTORY_DCIM),
    "${createNameByDate()}.jpg"
)

fun takePicture(
    cameraController: LifecycleCameraController,
    file: File,
    context: Context,
    onPictureTaken: (uri: Uri) -> Unit
) {
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    cameraController.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let { uri ->
                    val inputBitmap = BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(uri)
                    )

                    val rotationMatrix = Matrix()
                    rotationMatrix.postRotate(-90f)
                    val rotatedBitmap = Bitmap.createBitmap(
                        inputBitmap,
                        0,
                        0,
                        inputBitmap.width,
                        inputBitmap.height,
                        rotationMatrix,
                        true
                    )

                    val outputStream = FileOutputStream(file)
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    outputStream.flush()
                    outputStream.close()

                    onPictureTaken(uri)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(
                    "takePicture::OnImageSavedCallback::onError",
                    exception.message ?: "Error"
                    )
                }
            }
        )
    }
}

