package com.zorindisplays.display.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainViewModel(
    val dataSource: JackpotDataSource
) : ViewModel() {
    override fun onCleared() {
        super.onCleared()
        // Fire and forget, but in NonCancellable context
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
             dataSource.stop()
        }
    }
}
