package ca.rofiant.app.ui.auth

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.rofiant.app.R
import ca.rofiant.app.ui.chat.AppViewModel

private enum class AuthMode { SignIn, ResetPassword, CreateAccount }

private val PillShape = RoundedCornerShape(28.dp)

@Composable
fun AuthScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val busy by viewModel.authBusy.collectAsStateWithLifecycle()
    val error by viewModel.authError.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(AuthMode.SignIn) }
    var email by remember { mutableStateOf(viewModel.rememberedEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(viewModel.rememberedEmail != null) }
    var resetSent by remember { mutableStateOf(false) }
    var resetCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var signUpConfirmationSent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_rofiant_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Rofiant",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (mode != AuthMode.SignIn) {
                Text(
                    when (mode) {
                        AuthMode.CreateAccount -> "Create your account"
                        else -> "Reset your password"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            themedTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                keyboardType = KeyboardType.Email,
            )

            if (mode == AuthMode.SignIn || mode == AuthMode.CreateAccount) {
                Spacer(Modifier.height(12.dp))
                themedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                )
            }

            if (mode == AuthMode.CreateAccount) {
                Spacer(Modifier.height(12.dp))
                themedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Confirm password",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                )
                if (confirmPassword.isNotBlank() && confirmPassword != password) {
                    Text(
                        "Passwords don't match",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (mode == AuthMode.SignIn) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                    Text(
                        "Remember me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            if (mode == AuthMode.SignIn) {
                PrimaryPillButton(
                    text = "Sign in",
                    busy = busy,
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    onClick = { viewModel.signInWithPassword(email.trim(), password, rememberMe) },
                )

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val url = viewModel.startGoogleOAuth()
                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                    },
                    enabled = !busy,
                    shape = PillShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google", fontWeight = FontWeight.Medium)
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    MutedTextLink("Create account") { mode = AuthMode.CreateAccount; signUpConfirmationSent = false }
                    MutedTextLink("Forgot password?") { mode = AuthMode.ResetPassword; resetSent = false }
                }
            } else if (mode == AuthMode.CreateAccount) {
                if (signUpConfirmationSent) {
                    Text(
                        "Check your email to confirm your account, then sign in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                } else {
                    PrimaryPillButton(
                        text = "Create account",
                        busy = busy,
                        enabled = !busy && email.isNotBlank() && password.isNotBlank() && password == confirmPassword,
                        onClick = { viewModel.signUp(email.trim(), password) { signUpConfirmationSent = true } },
                    )
                }
                Spacer(Modifier.height(4.dp))
                MutedTextLink("Back to sign in", center = true) { mode = AuthMode.SignIn }
            } else {
                PrimaryPillButton(
                    text = "Send reset link",
                    busy = busy,
                    enabled = !busy && email.isNotBlank(),
                    onClick = { viewModel.requestPasswordReset(email.trim()) { resetSent = true } },
                )

                if (resetSent) {
                    Text(
                        "Enter the code from that email, or tap the reset link and come back here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                    )
                    themedTextField(
                        value = resetCode,
                        onValueChange = { resetCode = it },
                        label = "Code from email",
                        keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.height(12.dp))
                    themedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = "New password",
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryPillButton(
                        text = "Reset password",
                        busy = busy,
                        enabled = !busy && resetCode.isNotBlank() && newPassword.isNotBlank(),
                        onClick = { viewModel.confirmPasswordReset(email.trim(), resetCode.trim(), newPassword) },
                    )
                }

                Spacer(Modifier.height(4.dp))
                MutedTextLink("Back to sign in", center = true) { mode = AuthMode.SignIn }
            }
        }
    }
}

@Composable
private fun PrimaryPillButton(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MutedTextLink(text: String, center: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = if (center) Modifier.fillMaxWidth() else Modifier,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun themedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = PillShape,
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password",
                    )
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}
