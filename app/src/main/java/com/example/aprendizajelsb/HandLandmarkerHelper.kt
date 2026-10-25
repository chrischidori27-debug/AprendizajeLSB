package com.example.aprendizajelsb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    context: Context,
    private val alRecibirResultado: (HandLandmarkerResult, Int, Int) -> Unit,
    private val alCambiarEstado: (String) -> Unit
) {

    private var detector: HandLandmarker? = null

    init {
        try {
            val opcionesBase = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val opciones = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(opcionesBase)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { resultado, imagen ->
                    alRecibirResultado(resultado, imagen.width, imagen.height)
                }                .setErrorListener { error ->
                    Log.e(ETIQUETA, "Error de MediaPipe", error)
                    alCambiarEstado("ERROR MediaPipe: ${error.message}")
                }
                .build()

            detector = HandLandmarker.createFromOptions(context, opciones)
            alCambiarEstado("Detector: listo")
        } catch (e: Throwable) {
            Log.e(ETIQUETA, "No se pudo crear el detector de manos", e)
            alCambiarEstado("ERROR al crear el detector: ${e.message}")
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
    }
}