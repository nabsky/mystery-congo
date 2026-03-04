package com.zorindisplays.display.model

import androidx.lifecycle.ViewModel
import com.zorindisplays.display.emulator.Emulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainViewModel : ViewModel() {
    val emulator = Emulator(scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
    val dataSource = EmulatorDataSource(emulator)
}
