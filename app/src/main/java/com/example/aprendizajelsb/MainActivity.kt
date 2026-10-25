package com.example.aprendizajelsb

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var mensajePermiso: TextView
    private lateinit var botonPermiso: Button
    private lateinit var textoEstado: TextView

    // Un solo hilo para analizar imágenes sin bloquear la interfaz
    private lateinit var ejecutorCamara: ExecutorService
    private var detectorMano: HandLandmarkerHelper? = null

    // Datos que se muestran en pantalla
    @Volatile private var estadoDetector = "Detector: cargando..."
    @Volatile private var estadoMano = "Mano: -"
    @Volatile private var fotogramasEnviados = 0
    @Volatile private var resultadosRecibidos = 0
    private var ultimaActualizacion = 0L

    // Recibe la respuesta del usuario al cuadro de permiso de cámara
    private val solicitarPermisoCamara =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) iniciarCamara() else mostrarSinPermiso()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        previewView = findViewById(R.id.previewView)
        mensajePermiso = findViewById(R.id.mensajePermiso)
        botonPermiso = findViewById(R.id.botonPermiso)
        textoEstado = findViewById(R.id.textoEstado)
        ejecutorCamara = Executors.newSingleThreadExecutor()

        botonPermiso.setOnClickListener {
            solicitarPermisoCamara.launch(Manifest.permission.CAMERA)
        }

        if (tienePermisoCamara()) {
            iniciarCamara()
        } else {
            solicitarPermisoCamara.launch(Manifest.permission.CAMERA)
        }
    }

    private fun tienePermisoCamara(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun mostrarSinPermiso() {
        mensajePermiso.visibility = View.VISIBLE
        botonPermiso.visibility = View.VISIBLE
    }

    private fun iniciarCamara() {
        mensajePermiso.visibility = View.GONE
        botonPermiso.visibility = View.GONE

        // Se crea en el hilo de análisis, antes de que llegue el primer fotograma
        ejecutorCamara.execute {
            if (detectorMano == null) {
                detectorMano = HandLandmarkerHelper(
                    this,
                    alRecibirResultado = { resultado -> registrarResultado(resultado) },
                    alCambiarEstado = { texto ->
                        estadoDetector = texto
                        actualizarPantalla()
                    }
                )
            }
        }

        val futuroProveedor = ProcessCameraProvider.getInstance(this)
        futuroProveedor.addListener({
            val proveedor = futuroProveedor.get()

            val vistaPrevia = Preview.Builder().build()
            vistaPrevia.setSurfaceProvider(previewView.surfaceProvider)

            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analisis.setAnalyzer(ejecutorCamara) { imagen ->
                val detector = detectorMano
                if (detector != null) {
                    fotogramasEnviados++
                    detector.detectar(imagen)
                } else {
                    imagen.close()
                }
            }

            try {
                proveedor.unbindAll()
                proveedor.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    vistaPrevia,
                    analisis
                )
            } catch (e: Exception) {
                Log.e("AprendizajeLSB", "No se pudo iniciar la cámara", e)
                estadoDetector = "ERROR de cámara: ${e.message}"
                actualizarPantalla()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Guarda el resultado de MediaPipe y refresca la pantalla unas 5 veces por segundo
    private fun registrarResultado(resultado: HandLandmarkerResult) {
        resultadosRecibidos++

        estadoMano = if (resultado.landmarks().isEmpty()) {
            "Mano: no detectada"
        } else {
            val puntos = resultado.landmarks()[0]
            val muneca = puntos[0]
            val lado = resultado.handednesses()[0][0].categoryName()
            "Mano: $lado | puntos: ${puntos.size}\n" +
                    "Muñeca: x=${"%.2f".format(muneca.x())} y=${"%.2f".format(muneca.y())}"
        }

        val ahora = SystemClock.uptimeMillis()
        if (ahora - ultimaActualizacion >= 200) {
            ultimaActualizacion = ahora
            actualizarPantalla()
        }
    }

    private fun actualizarPantalla() {
        val texto = "$estadoDetector\n" +
                "Fotogramas enviados: $fotogramasEnviados\n" +
                "Resultados recibidos: $resultadosRecibidos\n" +
                estadoMano
        runOnUiThread { textoEstado.text = texto }
    }

    override fun onDestroy() {
        super.onDestroy()
        ejecutorCamara.execute {
            detectorMano?.cerrar()
            detectorMano = null
        }
        ejecutorCamara.shutdown()
    }
}