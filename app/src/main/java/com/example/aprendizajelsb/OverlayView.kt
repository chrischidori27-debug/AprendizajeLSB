package com.example.aprendizajelsb

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

// Capa transparente que dibuja los puntos de la mano sobre la vista previa
class OverlayView(context: Context, atributos: AttributeSet?) : View(context, atributos) {

    @Volatile private var puntos: List<NormalizedLandmark> = emptyList()
    @Volatile private var anchoImagen = 1
    @Volatile private var altoImagen = 1

    private val pincelPunto = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }
    private val pincelLinea = Paint().apply {
        color = Color.WHITE
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    // Pares de puntos que forman el esqueleto de la mano
    private val conexiones = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        5 to 9, 9 to 10, 10 to 11, 11 to 12,
        9 to 13, 13 to 14, 14 to 15, 15 to 16,
        13 to 17, 17 to 18, 18 to 19, 19 to 20,
        0 to 17
    )

    fun actualizar(nuevosPuntos: List<NormalizedLandmark>, ancho: Int, alto: Int) {
        puntos = nuevosPuntos
        anchoImagen = ancho
        altoImagen = alto
        postInvalidate()
    }

    fun limpiar() {
        puntos = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val lista = puntos
        if (lista.size < 21) return

        // La vista previa rellena la pantalla recortando los bordes,
        // así que se aplica la misma escala y desplazamiento
        val escala = max(width.toFloat() / anchoImagen, height.toFloat() / altoImagen)
        val desplazamientoX = (width - anchoImagen * escala) / 2f
        val desplazamientoY = (height - altoImagen * escala) / 2f

        fun posX(p: NormalizedLandmark) = p.x() * anchoImagen * escala + desplazamientoX
        fun posY(p: NormalizedLandmark) = p.y() * altoImagen * escala + desplazamientoY

        for ((a, b) in conexiones) {
            canvas.drawLine(posX(lista[a]), posY(lista[a]), posX(lista[b]), posY(lista[b]), pincelLinea)
        }
        for (p in lista) {
            canvas.drawCircle(posX(p), posY(p), 9f, pincelPunto)
        }
    }
}