package com.example.questly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.network.QuestlyApi
import com.example.questly.core.network.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(private val api: QuestlyApi, private val tokens: TokenStore) : ViewModel() {
    val signedIn = tokens.signedIn
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun login(email: String, password: String) = viewModelScope.launch {
        runCatching { api.login(email, password) }.onSuccess { }
            .onFailure { _message.value = "Sign-in failed. Verify your email and try again." }
    }
    fun register(email: String, password: String, name: String) = viewModelScope.launch {
        runCatching { api.register(email, password, name) }.onSuccess { _message.value = "Check your inbox to verify your account, then sign in." }
            .onFailure { _message.value = "Couldn’t create the account." }
    }
}

@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val viewModel: AuthViewModel = hiltViewModel()
    val signedIn by viewModel.signedIn.collectAsState()
    if (signedIn) content() else AuthScreen(viewModel)
}

@Composable
private fun AuthScreen(viewModel: AuthViewModel) {
    // Keep typed values across recomposition and configuration changes (e.g. a rotation).
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val message by viewModel.message.collectAsState()
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Welcome to Questly")
        Text("Sign in to keep your quests and points in sync.")
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Display name (sign-up)") })
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") })
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") })
        Button({ viewModel.login(email, password) }, Modifier.fillMaxWidth()) { Text("Sign in") }
        Button({ viewModel.register(email, password, name) }, Modifier.fillMaxWidth()) { Text("Create account") }
        message?.let { Text(it) }
    }
}
