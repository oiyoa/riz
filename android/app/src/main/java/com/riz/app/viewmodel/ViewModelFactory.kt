package com.riz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.riz.app.di.AppContainer

class ViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(MessageViewModel::class.java) -> {
                MessageViewModel(container.securityRepository) as T
            }
            modelClass.isAssignableFrom(FileViewModel::class.java) -> {
                FileViewModel(container.fileRepository, container.securityRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
}
