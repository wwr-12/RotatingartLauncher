package com.app.ralaunch.core.di.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 权限管理器
 */
class PermissionManagerServiceV1(private val activity: ComponentActivity) {

    private var manageAllFilesLauncher: ActivityResultLauncher<Intent>? = null
    private var requestPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var currentCallback: PermissionCallback? = null

    interface PermissionCallback {
        fun onPermissionsGranted()
        fun onPermissionsDenied()
    }

    fun initialize() {
        requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val allGranted = result.values.all { it }
            currentCallback?.let {
                if (allGranted) it.onPermissionsGranted() else it.onPermissionsDenied()
            }
            currentCallback = null
        }

        manageAllFilesLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                currentCallback?.let {
                    if (Environment.isExternalStorageManager()) it.onPermissionsGranted()
                    else it.onPermissionsDenied()
                }
                currentCallback = null
            }
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun requestRequiredPermissions(callback: PermissionCallback) {
        currentCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                manageAllFilesLauncher?.launch(intent)
            } catch (e: Exception) {
                manageAllFilesLauncher?.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissionLauncher?.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    fun requestNotificationPermission(callback: PermissionCallback?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentCallback = callback
            requestPermissionLauncher?.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            callback?.onPermissionsGranted()
        }
    }
}
