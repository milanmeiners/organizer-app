package com.lifeorganizer.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.lifeorganizer.app.data.Backup
import com.lifeorganizer.app.ui.AppRoot
import com.lifeorganizer.app.ui.MainViewModel
import com.lifeorganizer.app.ui.OrganizerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val json = Backup.toJson(vm.repo.snapshot())
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: throw IllegalStateException("Could not open file")
                }
                vm.message = "Backup saved"
            } catch (e: Exception) {
                vm.message = "Export failed: ${e.message}"
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IllegalStateException("Could not open file")
                }
                val data = Backup.fromJson(text)
                vm.repo.replaceAll(data)
                vm.message = "Backup imported: ${data.tasks.size} tasks, ${data.events.size} events, ${data.goals.size} goals"
            } catch (e: Exception) {
                vm.message = "Import failed: ${e.message}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OrganizerTheme {
                AppRoot(
                    vm = vm,
                    onExport = { exportLauncher.launch("organizer-backup-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
    }
}
