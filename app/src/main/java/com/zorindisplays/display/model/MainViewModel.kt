package com.zorindisplays.display.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainViewModel(
    val dataSource: JackpotStateDataSource
) : ViewModel() {
    override fun onCleared() {
        super.onCleared()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            dataSource.stop()
        }
    }
}
