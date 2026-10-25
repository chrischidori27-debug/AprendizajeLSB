package com.example.aprendizajelsb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    context: Context,
    private val alRecibirResultado: (HandLandmarkerResult, Int, Int) -> Unit,
    private val alCambiarEstado: (String) -> Unit
) {

    private var detector: HandLandmarker? = null
    private var usandoGpu = false

    init {
        detector = crearDetector(context, USAR_GPU)
        if (detector == null && USAR_GPU) {
            alCambiarEstado("GPU no disponible, probando con CPU...")
            detector = crearDetector(context, false)
        }
        if (detector != null) {
            alCambiarEstado("Detector: listo (${if (usandoGpu) "GPU" else "CPU"})")
        }
    }

    private fun crearDetector(context: Context, gpu: Boolean): HandLandmarker? {
        return try {
            val opcionesBase = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
            if (gpu) opcionesBase.setDelegate(Delegate.GPU)

            val opciones = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(opcionesBase.build())
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { resultado, imagen ->
                    alRecibirResultado(resultado, imagen.width, imagen.height)
                }
                .setErrorListener { error ->
                    Log.e(ETIQUETA, "Error de MediaPipe", error)
                    alCambiarEstado("ERROR MediaPipe: ${error.message}")
                }
                .build()

            val creado = HandLandmarker.createFromOptions(context, opciones)
            usandoGpu = gpu
            creado
        } catch (e: Throwable) {
            Log.e(ETIQUETA, "No se pudo crear el detector (gpu=$gpu)", e)
            alCambiarEstado("ERROR al crear el detector (${if (gpu) "GPU" else "CPU"}): ${e.message}")
            null
        }
    }

    // Recibe un fotograma de la cámara, lo prepara y lo envía a MediaPipe
    fun detectar(imagen: ImageProxy) {
        val deteccion = detector
        if (deteccion == null) {
            imagen.close()
            return
        }

        try {
            val rotacion = imagen.imageInfo.rotationDegrees
            val bitmap = imagen.use { it.toBitmap() }

            // Gira la imagen a la orientación correcta y la refleja (cámara frontal)
            val matriz = Matrix().apply {
                postRotate(rotacion.toFloat())
                postScale(-1f, 1f)
            }
            val preparado = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true
            )

            val imagenMp = BitmapImageBuilder(preparado).build()
            deteccion.detectAsync(imagenMp, SystemClock.uptimeMillis())
        } catch (e: Throwable) {
            Log.e(ETIQUETA, "Error al procesar la imagen", e)
            alCambiarEstado("ERROR al procesar imagen: ${e.message}")
        }
    }

    fun cerrar() {
        detector?.close()
        detector = null
    }

    companion object {
        private const val ETIQUETA = "AprendizajeLSB"

        // true = intenta usar la GPU; false = solo CPU
        private const val USAR_GPU = true
    }
}