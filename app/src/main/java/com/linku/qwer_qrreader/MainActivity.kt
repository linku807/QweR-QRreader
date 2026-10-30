package com.linku.qwer_qrreader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

import com.linku.qwer_qrreader.ui.theme.QweRQReaderTheme
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

class GlobalManager{
    var count by mutableIntStateOf(0)
    var currentText by mutableStateOf("N/A")

    var scansPerSecond by mutableIntStateOf(0)

    var UpscalePerSecond by mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    private var manager : GlobalManager = GlobalManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())
        enableEdgeToEdge()
        setContent {
            QweRQReaderTheme (dynamicColor = false){
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionCheck {
                        CameraPreviewScreen(manager)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCheck(content: @Composable () -> Unit){
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {isGranted ->
            hasCameraPermission = isGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission){
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        content()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
            Text("카메라 권한을 부여해주세요")
        }

    }
}

@Composable
fun CameraPreviewScreen(manager: GlobalManager) {
    Box(modifier = Modifier.fillMaxSize()){
        Box(modifier = Modifier.fillMaxSize()) {
        ComposeCameraXView(manager)
        }
        Column(){
            Box(modifier= Modifier.weight(1f))
            Box(modifier = Modifier
                .height(250.dp)
            ){
                TransparentGradient(modifier = Modifier.fillMaxSize())
                Surface (
                    modifier = Modifier.fillMaxSize().padding(15.dp,15.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, color = Color(0xB58374D0))
                ){
                    Column(modifier = Modifier.padding(18.dp,18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                            ) {
                            Surface (
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, color = Color(0xB58374D0))
                            ){
                                Column(
                                    modifier = Modifier.padding(5.dp, 5.dp),
                                ) {
                                    Text("초당 인식률", style = TextStyle(color = Color(0xff9a90d4), fontSize = 13.sp, fontWeight = FontWeight(700)))
                                    Box(Modifier.height(15.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(manager.scansPerSecond.toString(), style = TextStyle(fontSize = 20.sp,fontWeight = FontWeight(900)))
                                        Box(modifier = Modifier.width(2.dp))
                                        Text("FPS", style = TextStyle(color = Color(0xff783cf1), fontSize = 12.sp, fontWeight = FontWeight(700)))
                                    }
                                }
                            }
                            Box(modifier = Modifier.width(7.dp))
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, color = Color(0xB58374D0))
                            ) {
                                Column(
                                    modifier = Modifier.padding(5.dp, 5.dp),
                                ) {
                                    Text("개선된 초당 인식률", style = TextStyle(color = Color(0xff9a90d4), fontSize = 13.sp, fontWeight = FontWeight(700)))
                                    Box(Modifier.height(15.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text((manager.scansPerSecond+manager.UpscalePerSecond).toString(), style = TextStyle(fontSize = 20.sp,fontWeight = FontWeight(900)))
                                        Box(modifier = Modifier.width(2.dp))
                                        Text("FPS", style = TextStyle(color = Color(0xff783cf1), fontSize = 12.sp, fontWeight = FontWeight(700)))
                                    }
                                }
                            }
                            Box(modifier = Modifier.width(7.dp))
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, color = Color(0xB58374D0))
                            ) {
                                Column(
                                    modifier = Modifier.padding(5.dp, 5.dp),
                                ) {
                                    Text("누적 인식", style = TextStyle(color = Color(0xff9a90d4), fontSize = 13.sp, fontWeight = FontWeight(700)))
                                    Box(Modifier.height(15.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(manager.count.toString(), style = TextStyle(fontSize = 20.sp,fontWeight = FontWeight(900)))
                                        Box(modifier = Modifier.width(2.dp))
                                        Text("회", style = TextStyle(color = Color(0xff783cf1), fontSize = 12.sp, fontWeight = FontWeight(700)))
                                    }
                                }
                            }

                        }
                        Box(Modifier.height(5.dp))
                        Text("디코딩된 텍스트열", style = TextStyle(color = Color(0xff9a90d4), fontSize = 12.sp, fontWeight = FontWeight(700)))
                        Box(Modifier.height(5.dp))
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, color = Color(0xC1675DA0))
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    manager.currentText,
                                    style = TextStyle(
                                        color = Color(0xFFCEC6E5),
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComposeCameraXView(manager: GlobalManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val surfaceRequests = remember { MutableStateFlow<SurfaceRequest?>(null) }
    val surfaceRequest by surfaceRequests.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { req -> surfaceRequests.value = req }
        }

        // 이미지 분석 유스케이스 생성
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {
                setAnalyzer(
                    // executor
                    ContextCompat.getMainExecutor(context),
                    // analyzer
                    QRAnalyzer(context) { scannedText, updateCount, upscaleCount->
                        if (!scannedText.isNullOrEmpty()) {
                            manager.count++
                            manager.currentText = scannedText
                        }
                        if (updateCount != null && upscaleCount != null){
                            manager.scansPerSecond = updateCount
                            manager.UpscalePerSecond = upscaleCount
                        }
                    },
                )
            }

        provider.unbindAll()

        /// 이미지 분석 추가
        provider.bindToLifecycle(
            lifecycleOwner = lifecycleOwner,
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            // 카메라 프리뷰에 이미지 분석 유스케이스 추가
            imageAnalyzer,
        )
    }

    surfaceRequest?.let {
        CameraXViewfinder(
            surfaceRequest = it,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun TransparentGradient(modifier : Modifier = Modifier) {
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.7f),
            Color.Black
        )
    )

    Box(
        modifier = modifier.background(brush = gradientBrush)
    )
}


