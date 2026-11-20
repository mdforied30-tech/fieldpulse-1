package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.model.AppMode
import com.example.data.model.SUPER_ADMIN_EMAIL
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.FieldPulseTheme
import com.example.ui.theme.Mist100
import com.example.ui.theme.Obsidian900
import com.example.ui.theme.Obsidian950
import com.example.ui.theme.Slate300
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private const val PREFS_NAME = "fieldpulse_portal_prefs"
private const val KEY_LOGGED_IN = "portal_logged_in"
private const val KEY_PORTAL_MODE = "portal_mode"
private const val KEY_PORTAL_EMAIL = "portal_email"
private const val KEY_PORTAL_NAME = "portal_name"
private const val KEY_PORTAL_PHONE = "portal_phone"
private const val KEY_PORTAL_DEPT = "portal_dept"
private const val KEY_WORKER_ACCOUNTS = "worker_accounts_json"

// Fixed SHA-256 digest for the authorized Super Admin password
private const val FIXED_ADMIN_PASSWORD_SHA256 =
    "9c955a7e0ebcb1b6a34d6ed2ad827b47d0ef9240d697f013c9ba1f289c5144ea"

data class PortalSession(
    val loggedIn: Boolean = false,
    val mode: AppMode = AppMode.WORKER,
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val department: String = "Field Operations"
)

data class SavedWorkerAccount(
    val email: String,
    val name: String,
    val phone: String,
    val department: String,
    val passwordSha256: String
)

object PortalAuthStore {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sha256Hex(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyAdminCredentials(email: String, password: String): Boolean {
        return email.trim().equals(SUPER_ADMIN_EMAIL, ignoreCase = true) &&
            sha256Hex(password) == FIXED_ADMIN_PASSWORD_SHA256
    }

    fun loadSession(context: Context): PortalSession {
        val p = prefs(context)
        val loggedIn = p.getBoolean(KEY_LOGGED_IN, false)
        val rawMode = p.getString(KEY_PORTAL_MODE, AppMode.WORKER.name)
        val mode = AppMode.entries.firstOrNull { it.name == rawMode } ?: AppMode.WORKER
        return PortalSession(
            loggedIn = loggedIn,
            mode = mode,
            email = p.getString(KEY_PORTAL_EMAIL, "").orEmpty(),
            name = p.getString(KEY_PORTAL_NAME, "").orEmpty(),
            phone = p.getString(KEY_PORTAL_PHONE, "").orEmpty(),
            department = p.getString(KEY_PORTAL_DEPT, "Field Operations").orEmpty()
        )
    }

    fun saveActiveSession(context: Context, session: PortalSession) {
        prefs(context).edit()
            .putBoolean(KEY_LOGGED_IN, session.loggedIn)
            .putString(KEY_PORTAL_MODE, session.mode.name)
            .putString(KEY_PORTAL_EMAIL, session.email.trim())
            .putString(KEY_PORTAL_NAME, session.name.trim())
            .putString(KEY_PORTAL_PHONE, session.phone.trim())
            .putString(KEY_PORTAL_DEPT, session.department.trim().ifBlank { "Field Operations" })
            .apply()
    }

    fun clearActiveSession(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
    }

    fun getSavedWorkerAccounts(context: Context): List<SavedWorkerAccount> {
        val raw = prefs(context).getString(KEY_WORKER_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        SavedWorkerAccount(
                            email = obj.optString("email"),
                            name = obj.optString("name"),
                            phone = obj.optString("phone"),
                            department = obj.optString("department", "Field Operations"),
                            passwordSha256 = obj.optString("passwordSha256")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun registerWorkerAccount(
        context: Context,
        name: String,
        email: String,
        phone: String,
        department: String,
        password: String
    ): Result<SavedWorkerAccount> {
        val cleanEmail = email.trim().lowercase()
        val cleanName = name.trim()
        if (cleanEmail.equals(SUPER_ADMIN_EMAIL, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Reserved Admin email cannot be used as a Worker account."))
        }
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Worker full name is required."))
        }
        if (!cleanEmail.contains("@") || cleanEmail.length < 5) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Password must be at least 4 characters."))
        }

        val account = SavedWorkerAccount(
            email = cleanEmail,
            name = cleanName,
            phone = phone.trim(),
            department = department.trim().ifBlank { "Field Operations" },
            passwordSha256 = sha256Hex(password)
        )
        val updated = getSavedWorkerAccounts(context)
            .filterNot { it.email.equals(cleanEmail, ignoreCase = true) } + account

        val arr = JSONArray()
        updated.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("email", item.email)
                    put("name", item.name)
                    put("phone", item.phone)
                    put("department", item.department)
                    put("passwordSha256", item.passwordSha256)
                }
            )
        }
        prefs(context).edit().putString(KEY_WORKER_ACCOUNTS, arr.toString()).apply()
        return Result.success(account)
    }

    fun authenticateWorker(context: Context, email: String, password: String): Result<SavedWorkerAccount> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.equals(SUPER_ADMIN_EMAIL, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Use the Admin Login tab for Admin credentials."))
        }
        val match = getSavedWorkerAccounts(context).firstOrNull {
            it.email.equals(cleanEmail, ignoreCase = true)
        } ?: return Result.failure(
            IllegalArgumentException("No saved worker found for $cleanEmail. Tap 'Register Worker' to create this account.")
        )

        return if (sha256Hex(password) == match.passwordSha256) {
            Result.success(match)
        } else {
            Result.failure(IllegalArgumentException("Incorrect password for $cleanEmail."))
        }
    }
}

suspend fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager
): FirebaseUser? {
    Firebase.auth.currentUser?.let { return it }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        return null
    }
    return try {
        val activity = context as? Activity ?: return null
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val cred = credentialManager.getCredential(activity, request).credential
        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
            Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user
        } else {
            null
        }
    } catch (_: NoCredentialException) {
        null
    } catch (_: GetCredentialCancellationException) {
        null
    } catch (e: Exception) {
        Log.d("Auth", "Silent cloud sync skipped: ${e.message}")
        null
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onCloudLinked: (FirebaseUser?) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activity = context as? Activity
                ?: throw IllegalStateException("Activity context required for Cloud Sync setup")
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = Firebase.auth
                    .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                    .await()
                onCloudLinked(authResult.user)
            } else {
                onAuthError("Unexpected credential type returned by Google Sign-In")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Cloud link cancelled: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Cloud link failed", e)
            onAuthError(e.localizedMessage ?: "Cloud connection failed")
        }
    }
}

fun signOutPortalOnly(
    context: Context,
    onPortalSignedOut: () -> Unit
) {
    PortalAuthStore.clearActiveSession(context)
    onPortalSignedOut()
}

fun signOutCloudAccount(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    PortalAuthStore.clearActiveSession(context)
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    firebaseUser: FirebaseUser?,
    externalError: String? = null,
    onClearExternalError: () -> Unit = {},
    onAuthSuccess: (PortalSession) -> Unit
) {
    // Always render AuthScreen in dark tactical theme so light-mode devices maintain crisp contrast
    FieldPulseTheme(darkTheme = true) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        var selectedTab by remember { mutableIntStateOf(0) } // 0 = Admin Login, 1 = Workers Login
        var workerRegisterMode by remember { mutableStateOf(false) }

        // Admin fields
        var adminEmail by remember { mutableStateOf("") }
        var adminPassword by remember { mutableStateOf("") }
        var adminPasswordVisible by remember { mutableStateOf(false) }

        // Worker fields
        var workerName by remember { mutableStateOf("") }
        var workerEmail by remember { mutableStateOf("") }
        var workerPhone by remember { mutableStateOf("") }
        var workerDept by remember { mutableStateOf("Field Operations") }
        var workerPassword by remember { mutableStateOf("") }
        var workerPasswordVisible by remember { mutableStateOf(false) }

        var savedWorkers by remember { mutableStateOf(PortalAuthStore.getSavedWorkerAccounts(context)) }
        var cloudLinking by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val activeError = externalError ?: errorMessage

        val isCloudLinked = firebaseUser != null
        val isCloudVerifiedAdmin = firebaseUser != null &&
            firebaseUser.email.equals(SUPER_ADMIN_EMAIL, ignoreCase = true) &&
            firebaseUser.isEmailVerified

        LaunchedEffect(Unit) {
            if (firebaseUser == null) {
                attemptAutoSignIn(context, credentialManager)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Obsidian950, Obsidian900, Obsidian950)
                    )
                )
                .systemBarsPadding()
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Command Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(152.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.img_command_hero),
                            contentDescription = "FieldPulse tactical command banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Obsidian950.copy(alpha = 0.92f))
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_fieldpulse_badge),
                                contentDescription = "FieldPulse emblem",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Emerald400.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.sign_in_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "COMMAND & FIELD TELEMETRY",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Emerald400
                                )
                            }
                        }
                    }
                }

                // Header with crisp high-contrast text
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Select Access Portal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Mist100
                    )
                    Text(
                        text = "Sign in directly with Email & Password. Your active session stays saved so you won't need to re-enter your password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate300
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeaturePill(Icons.Filled.LocationOn, "Location", Emerald400, Modifier.weight(1f))
                        FeaturePill(Icons.Filled.Videocam, "Camera", Cyan400, Modifier.weight(1f))
                        FeaturePill(Icons.Filled.Mic, "Microphone", Emerald400, Modifier.weight(1f))
                    }
                }

                // One-time Cloud Backend Link Banner (only prominently shown if Firebase Auth is not yet linked)
                if (!isCloudLinked) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, Cyan400.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    tint = Cyan400
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "One-Time Cloud Sync Setup",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Mist100,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Link this device once to Firebase Cloud so GPS, Camera, and Microphone sync in real time. After linking, you only use Email & Password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate300
                            )
                            Button(
                                onClick = {
                                    errorMessage = null
                                    cloudLinking = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onCloudLinked = { cloudLinking = false },
                                        onAuthError = { msg ->
                                            cloudLinking = false
                                            errorMessage = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = { cloudLinking = false }
                                    )
                                },
                                enabled = !cloudLinking,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("connect_cloud_button")
                            ) {
                                if (cloudLinking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Obsidian950
                                    )
                                } else {
                                    Text("Connect Cloud Sync (One-Time)")
                                }
                            }
                        }
                    }
                }

                if (activeError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = activeError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Direct Email & Password Portal Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(
                        1.dp,
                        if (selectedTab == 0) Emerald400.copy(alpha = 0.45f) else Cyan400.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = {
                                    selectedTab = 0
                                    errorMessage = null
                                    onClearExternalError()
                                },
                                text = { Text("Admin Login") },
                                icon = {
                                    Icon(
                                        Icons.Filled.AdminPanelSettings,
                                        contentDescription = "Admin Login Tab",
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("admin_portal_tab")
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = {
                                    selectedTab = 1
                                    errorMessage = null
                                    onClearExternalError()
                                },
                                text = { Text("Workers Login") },
                                icon = {
                                    Icon(
                                        Icons.Filled.Engineering,
                                        contentDescription = "Workers Login Tab",
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("worker_portal_tab")
                            )
                        }

                        if (selectedTab == 0) {
                            // ADMIN LOGIN (Strictly fixed email & password)
                            Text(
                                text = "Authorized Super Admin Access Only",
                                style = MaterialTheme.typography.titleMedium,
                                color = Emerald400,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enter the fixed Super Admin email and password. Unauthorized credentials cannot access the Admin Control Center.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate300
                            )

                            OutlinedTextField(
                                value = adminEmail,
                                onValueChange = { adminEmail = it },
                                label = { Text("Admin Email (Gmail)") },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_email_input")
                            )

                            OutlinedTextField(
                                value = adminPassword,
                                onValueChange = { adminPassword = it },
                                label = { Text("Admin Password") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { adminPasswordVisible = !adminPasswordVisible }) {
                                        Icon(
                                            imageVector = if (adminPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                visualTransformation = if (adminPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_password_input")
                            )

                            if (isCloudLinked && !isCloudVerifiedAdmin) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Device Cloud Sync is linked to ${firebaseUser?.email}. To read all workers securely, switch Cloud Sync to $SUPER_ADMIN_EMAIL.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate300
                                        )
                                        TextButton(
                                            onClick = {
                                                signOutCloudAccount(context, credentialManager, scope) {
                                                    onGoogleSignInClicked(
                                                        context = context,
                                                        credentialManager = credentialManager,
                                                        onCloudLinked = {},
                                                        onAuthError = { errorMessage = it },
                                                        scope = scope
                                                    )
                                                }
                                            }
                                        ) {
                                            Text("Verify Admin Cloud Account")
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    errorMessage = null
                                    onClearExternalError()
                                    when {
                                        !PortalAuthStore.verifyAdminCredentials(adminEmail, adminPassword) -> {
                                            errorMessage = "Invalid Admin email or password. Only authorized Admin can log in here."
                                        }
                                        firebaseUser == null -> {
                                            errorMessage = "Please tap 'Connect Cloud Sync (One-Time)' above first so live telemetry can load."
                                        }
                                        !isCloudVerifiedAdmin -> {
                                            errorMessage = "Cloud Sync is currently linked to ${firebaseUser.email}. Tap 'Verify Admin Cloud Account' above with $SUPER_ADMIN_EMAIL."
                                        }
                                        else -> {
                                            val session = PortalSession(
                                                loggedIn = true,
                                                mode = AppMode.ADMIN,
                                                email = SUPER_ADMIN_EMAIL,
                                                name = "Super Admin",
                                                phone = "",
                                                department = "Command Center"
                                            )
                                            PortalAuthStore.saveActiveSession(context, session)
                                            onAuthSuccess(session)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald500,
                                    contentColor = Obsidian950
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("admin_login_button")
                            ) {
                                Text("Admin Login", style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            // WORKERS LOGIN / REGISTER (Direct Email + Password, NO Google Chooser)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !workerRegisterMode,
                                    onClick = {
                                        workerRegisterMode = false
                                        errorMessage = null
                                    },
                                    label = { Text("Worker Sign In") },
                                    modifier = Modifier.weight(1f).testTag("worker_signin_mode_chip")
                                )
                                FilterChip(
                                    selected = workerRegisterMode,
                                    onClick = {
                                        workerRegisterMode = true
                                        errorMessage = null
                                    },
                                    label = { Text("Register Worker") },
                                    modifier = Modifier.weight(1f).testTag("worker_register_mode_chip")
                                )
                            }

                            if (!workerRegisterMode && savedWorkers.isNotEmpty()) {
                                Text(
                                    text = "Saved Worker Accounts (tap to fill):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Cyan400
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    savedWorkers.forEach { saved ->
                                        AssistChip(
                                            onClick = {
                                                workerEmail = saved.email
                                                errorMessage = null
                                            },
                                            label = { Text("${saved.name} (${saved.email})") }
                                        )
                                    }
                                }
                            }

                            if (workerRegisterMode) {
                                OutlinedTextField(
                                    value = workerName,
                                    onValueChange = { workerName = it },
                                    label = { Text("Worker Full Name") },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("worker_reg_name_input")
                                )
                                OutlinedTextField(
                                    value = workerPhone,
                                    onValueChange = { workerPhone = it },
                                    label = { Text("Phone Number") },
                                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("worker_reg_phone_input")
                                )
                                OutlinedTextField(
                                    value = workerDept,
                                    onValueChange = { workerDept = it },
                                    label = { Text("Department / Trade") },
                                    leadingIcon = { Icon(Icons.Filled.Work, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("worker_reg_dept_input")
                                )
                            }

                            OutlinedTextField(
                                value = workerEmail,
                                onValueChange = { workerEmail = it },
                                label = { Text("Worker Email (Gmail)") },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("worker_email_input")
                            )

                            OutlinedTextField(
                                value = workerPassword,
                                onValueChange = { workerPassword = it },
                                label = { Text(if (workerRegisterMode) "Create Password" else "Password") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { workerPasswordVisible = !workerPasswordVisible }) {
                                        Icon(
                                            imageVector = if (workerPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                visualTransformation = if (workerPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("worker_password_input")
                            )

                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    onClearExternalError()
                                    if (firebaseUser == null) {
                                        errorMessage = "Please tap 'Connect Cloud Sync (One-Time)' above first so telemetry can sync to Admin."
                                        return@OutlinedButton
                                    }
                                    if (workerRegisterMode) {
                                        PortalAuthStore.registerWorkerAccount(
                                            context = context,
                                            name = workerName,
                                            email = workerEmail,
                                            phone = workerPhone,
                                            department = workerDept,
                                            password = workerPassword
                                        ).onSuccess { account ->
                                            savedWorkers = PortalAuthStore.getSavedWorkerAccounts(context)
                                            val session = PortalSession(
                                                loggedIn = true,
                                                mode = AppMode.WORKER,
                                                email = account.email,
                                                name = account.name,
                                                phone = account.phone,
                                                department = account.department
                                            )
                                            PortalAuthStore.saveActiveSession(context, session)
                                            onAuthSuccess(session)
                                        }.onFailure { err ->
                                            errorMessage = err.message
                                        }
                                    } else {
                                        PortalAuthStore.authenticateWorker(
                                            context = context,
                                            email = workerEmail,
                                            password = workerPassword
                                        ).onSuccess { account ->
                                            val session = PortalSession(
                                                loggedIn = true,
                                                mode = AppMode.WORKER,
                                                email = account.email,
                                                name = account.name,
                                                phone = account.phone,
                                                department = account.department
                                            )
                                            PortalAuthStore.saveActiveSession(context, session)
                                            onAuthSuccess(session)
                                        }.onFailure { err ->
                                            errorMessage = err.message
                                        }
                                    }
                                },
                                border = BorderStroke(1.5.dp, Cyan400),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("worker_login_button")
                            ) {
                                Text(
                                    text = if (workerRegisterMode) "Register & Enter Worker Panel" else "Workers Login",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Cyan400
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Cyan400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Subtle footer indicating Cloud Sync connection state
                if (isCloudLinked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cloud Synced (${firebaseUser?.email.orEmpty()})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate300
                            )
                        }
                        Text(
                            text = "Switch Cloud",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cyan400,
                            modifier = Modifier
                                .clickable {
                                    signOutCloudAccount(context, credentialManager, scope) {
                                        onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = credentialManager,
                                            onCloudLinked = {},
                                            onAuthError = { errorMessage = it },
                                            scope = scope
                                        )
                                    }
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturePill(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Mist100,
                maxLines = 1
            )
        }
    }
}
