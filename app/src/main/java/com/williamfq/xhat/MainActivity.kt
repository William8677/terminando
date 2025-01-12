package com.williamfq.xhat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.williamfq.xhat.ui.theme.XhatTheme
import com.williamfq.xhat.utils.ErrorHandler
import com.williamfq.xhat.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: PermissionsViewModel by viewModels()
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var isComponentsInitialized = false

    @Inject
    lateinit var errorHandler: ErrorHandler

    @Inject
    lateinit var logUtils: LogUtils

    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL_ID = "XhatChannel"
        private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

        private object Config {
            const val CURRENT_USER = "William8677"
            const val TASK_ID = "784"
            const val TIMESTAMP_UTC = "2025-01-11 18:59:01"
        }
    }

    private fun getDeviceInfo(): Map<String, Any> = mapOf(
        "android_version" to Build.VERSION.SDK_INT,
        "device_manufacturer" to Build.MANUFACTURER,
        "device_model" to Build.MODEL,
        "app_version" to BuildConfig.VERSION_NAME,
        "is_debug" to BuildConfig.DEBUG,
        "timestamp_utc" to Config.TIMESTAMP_UTC
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val startTime = System.currentTimeMillis()
        try {
            super.onCreate(savedInstanceState)
            installSplashScreen()

            setupPermissionLauncher()

            logUtils.logInfo(
                "Iniciando MainActivity",
                createLogInfo("onCreate", mapOf("has_saved_state" to (savedInstanceState != null)))
            )

            initializeComponents()
        } catch (e: Exception) {
            handleFatalError(e, "onCreate")
        } finally {
            val duration = System.currentTimeMillis() - startTime
            logUtils.logDebug("Tiempo de inicialización: $duration ms", createLogInfo("onCreate"))
        }
    }

    private fun setupPermissionLauncher() {
        permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissions.entries.forEach { (permission, isGranted) ->
                logUtils.logInfo(
                    "Permiso: $permission - ${if (isGranted) "Concedido" else "Denegado"}",
                    createLogInfo("setupPermissionLauncher")
                )
                if (!isGranted) {
                    showUserMessage("El permiso $permission es necesario para continuar.")
                }
            }
            if (permissions.values.all { it }) {
                logUtils.logSuccess(
                    "Todos los permisos concedidos",
                    createLogInfo("setupPermissionLauncher")
                )
                initializeNotifications()
                navigateToMainScreen()
            }
        }
    }

    private fun initializeComponents() {
        mainScope.launch {
            try {
                initializeNotifications()
                initializePermissionsHandling()
                setupUserInterface()
                isComponentsInitialized = true
                logUtils.logSuccess(
                    "Componentes inicializados correctamente",
                    createLogInfo("initializeComponents", getDeviceInfo())
                )
            } catch (e: Exception) {
                handleError(e, "initializeComponents")
            }
        }
    }

    private fun initializePermissionsHandling() {
        val permissionsToCheck = mapOf(
            Manifest.permission.POST_NOTIFICATIONS to "Notificaciones",
            Manifest.permission.CAMERA to "Cámara",
            Manifest.permission.RECORD_AUDIO to "Micrófono",
            Manifest.permission.ACCESS_FINE_LOCATION to "Ubicación"
        )

        permissionsToCheck.forEach { (permission, name) ->
            val isGranted = ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED
            logUtils.logInfo(
                "Estado de permiso - $name: ${if (isGranted) "Concedido" else "Pendiente"}",
                createLogInfo("initializePermissionsHandling")
            )
        }
    }

    private fun setupUserInterface() {
        setContent {
            XhatTheme {
                val permissionsGranted by viewModel.permissionsGranted.collectAsState()
                MainScreen(
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = {
                        logUtils.logDebug(
                            "Solicitando permisos desde UI",
                            createLogInfo("setupUserInterface")
                        )
                        checkAndRequestPermissions()
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val sharedPreferences = getSharedPreferences("XhatPrefs", MODE_PRIVATE)
        val permissionsRequested = sharedPreferences.getBoolean("permissionsRequested", false)

        // Si ya se solicitaron permisos antes, no volvemos a mostrarlos
        if (permissionsRequested) {
            logUtils.logInfo("Los permisos ya fueron solicitados previamente.")
            navigateToMainScreen()
            return
        }

        // Lista de permisos requeridos
        val requiredPermissions = listOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Filtrar permisos no concedidos
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Lanzar solicitud de permisos
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())

            // Guardar estado para evitar futuras solicitudes
            sharedPreferences.edit().putBoolean("permissionsRequested", true).apply()
        } else {
            logUtils.logSuccess("Todos los permisos ya están concedidos.")
            navigateToMainScreen()
        }
    }

    private fun navigateToMainScreen() {
        setContent {
            XhatTheme {
                MainScreen(
                    permissionsGranted = true,
                    onRequestPermissions = { checkAndRequestPermissions() }
                )
            }
        }
    }

    private fun initializeNotifications() {
        createNotificationChannel()
        logUtils.logSuccess(
            "Sistema de notificaciones inicializado",
            createLogInfo("initializeNotifications")
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Xhat Notificaciones",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal de notificaciones para Xhat"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun handleError(error: Exception, source: String) {
        logUtils.logError(
            "Error en $source: ${error.message}",
            error,
            createLogInfo(source, mapOf("error_type" to error.javaClass.simpleName))
        )
        errorHandler.handleException(error)
        showUserMessage("Ha ocurrido un error")
    }

    private fun handleFatalError(error: Exception, source: String) {
        logUtils.logError(
            "Error fatal en $source: ${error.message}",
            error,
            createLogInfo(source, mapOf(
                "error_type" to error.javaClass.simpleName,
                "fatal" to true
            ))
        )
        showUserMessage("Error fatal en la aplicación")
        finish()
    }

    private fun showUserMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    private fun createLogInfo(
        methodName: String,
        extraInfo: Map<String, Any?> = emptyMap()
    ): LogUtils.DetailedLogInfo {
        return LogUtils.DetailedLogInfo(
            className = TAG,
            methodName = methodName,
            lineNumber = Thread.currentThread().stackTrace[3].lineNumber,
            user = Config.CURRENT_USER,
            taskId = Config.TASK_ID,
            extraInfo = extraInfo + getDeviceInfo()
        )
    }
}
