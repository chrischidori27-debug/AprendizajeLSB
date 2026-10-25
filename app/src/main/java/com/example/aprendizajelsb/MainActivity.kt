package com.example.aprendizajelsb

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var mensajePermiso: TextView
    private lateinit var botonPermiso: Button

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

        val futuroProveedor = ProcessCameraProvider.getInstance(this)
        futuroProveedor.addListener({
            val proveedor = futuroProveedor.get()

            val vistaPrevia = Preview.Builder().build()
            vistaPrevia.setSurfaceProvider(previewView.surfaceProvider)

            try {
                proveedor.unbindAll()
                proveedor.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    vistaPrevia
                )
            } catch (e: Exception) {
                Log.e("AprendizajeLSB", "No se pudo iniciar la cámara", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
}