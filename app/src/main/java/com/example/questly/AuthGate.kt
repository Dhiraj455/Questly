package com.example.questly

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.network.QuestlyApi
import com.example.questly.core.network.TokenStore
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: QuestlyApi,
    private val tokens: TokenStore,
) : ViewModel() {
    val signedIn = tokens.signedIn

    data class Banner(val text: String, val isError: Boolean)

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner = _banner.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun login(email: String, password: String) = viewModelScope.launch {
        _loading.value = true; _banner.value = null
        runCatching { api.login(email.trim(), password) }
            .onFailure { _banner.value = Banner("Sign-in failed. Check your details and that your email is verified.", true) }
        _loading.value = false
    }

    fun register(email: String, password: String, name: String) = viewModelScope.launch {
        _loading.value = true; _banner.value = null
        runCatching { api.register(email.trim(), password, name.trim()) }
            .onSuccess { _banner.value = Banner("Account created — check your inbox to verify, then sign in.", false) }
            .onFailure { _banner.value = Banner("Couldn’t create the account. Try a different email.", true) }
        _loading.value = false
    }

    fun googleSignIn(idToken: String) = viewModelScope.launch {
        _loading.value = true; _banner.value = null
        runCatching { api.googleSignIn(idToken) }
            .onFailure { _banner.value = Banner("Google sign-in failed on the server. Please try again.", true) }
        _loading.value = false
    }

    fun error(text: String) { _banner.value = Banner(text, true) }
    fun clearBanner() { _banner.value = null }
}

@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val viewModel: AuthViewModel = hiltViewModel()
    val signedIn by viewModel.signedIn.collectAsState()
    if (signedIn) content() else AuthScreen(viewModel)
}

@Composable
private fun AuthScreen(viewModel: AuthViewModel) {
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val banner by viewModel.banner.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val emailValid = email.trim().contains("@") && email.trim().contains(".")
    val formValid = emailValid && password.length >= 8 && (!isSignUp || name.isNotBlank())

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            // Brand mark
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Explore, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Questly", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isSignUp) "Create an account to start earning points" else "Welcome back — sign in to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            if (isSignUp) {
                QuestlyField(
                    value = name, onChange = { name = it }, label = "Display name",
                    leading = Icons.Filled.Person,
                    keyboard = KeyboardOptions(imeAction = ImeAction.Next, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                )
                Spacer(Modifier.height(12.dp))
            }
            QuestlyField(
                value = email, onChange = { email = it }, label = "Email",
                leading = Icons.Filled.Email,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            QuestlyField(
                value = password, onChange = { password = it }, label = "Password",
                leading = Icons.Filled.Lock,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                supporting = if (isSignUp) "At least 8 characters" else null,
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (isSignUp) viewModel.register(email, password, name) else viewModel.login(email, password) },
                enabled = formValid && !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (isSignUp) "Create account" else "Sign in", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  or  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = { scope.launch { signInWithGoogle(context, viewModel) } },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                GoogleGlyph()
                Spacer(Modifier.width(12.dp))
                Text("Continue with Google", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { isSignUp = !isSignUp; viewModel.clearBanner() }) {
                Text(
                    if (isSignUp) "Already have an account? Sign in" else "New to Questly? Create an account",
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            banner?.let {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = if (it.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        it.text,
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (it.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuestlyField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    keyboard: KeyboardOptions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(leading, contentDescription = null) },
        trailingIcon = trailing,
        singleLine = true,
        keyboardOptions = keyboard,
        visualTransformation = visualTransformation,
        supportingText = supporting?.let { { Text(it) } },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Minimal multi-color "G" so the button reads as Google without shipping the trademarked logo asset. */
@Composable
private fun GoogleGlyph() {
    Text("G", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF4285F4))
}

/** Drives Credential Manager's Sign in with Google, surfacing the real failure reason. */
private suspend fun signInWithGoogle(context: Context, viewModel: AuthViewModel) {
    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    if (clientId.isBlank()) {
        viewModel.error("Google sign-in isn’t configured for this build.")
        return
    }
    val option = GetSignInWithGoogleOption.Builder(clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    try {
        val result = CredentialManager.create(context).getCredential(context, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            viewModel.googleSignIn(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            viewModel.error("Unexpected credential type from Google.")
        }
    } catch (_: GetCredentialCancellationException) {
        // User dismissed the sheet — no error banner needed.
    } catch (_: NoCredentialException) {
        viewModel.error("No Google account on this device. Add one in Settings → Passwords & accounts, then try again.")
    } catch (e: GetCredentialException) {
        Log.e("AuthGate", "Google sign-in failed", e)
        viewModel.error("Google sign-in failed: ${e.errorMessage ?: e.javaClass.simpleName}")
    }
}
