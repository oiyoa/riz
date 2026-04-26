package com.riz.app.di

import android.content.Context
import com.riz.app.crypto.DerivedKeyPool
import com.riz.app.crypto.DerivedKeyStore
import com.riz.app.data.PasswordStore
import com.riz.app.data.repository.FileRepository
import com.riz.app.data.repository.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Simple manual dependency injection container.
 */
class AppContainer(
    private val context: Context,
) {
    val passwordStore: PasswordStore by lazy {
        PasswordStore(context)
    }

    private val derivedKeyStore: DerivedKeyStore by lazy {
        DerivedKeyStore(context)
    }

    private val keyPool: DerivedKeyPool by lazy {
        DerivedKeyPool(
            store = derivedKeyStore,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    val securityRepository: SecurityRepository by lazy {
        SecurityRepository(passwordStore, keyPool)
    }

    val fileRepository: FileRepository by lazy {
        FileRepository(context)
    }
}
