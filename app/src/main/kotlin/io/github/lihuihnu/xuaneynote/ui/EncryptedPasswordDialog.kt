package io.github.lihuihnu.xuaneynote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.github.lihuihnu.xuaneynote.R

internal enum class EncryptedPasswordMode { EXPORT, IMPORT }

/**
 * Never save password into persisted state, Room or DataStore.
 * Password Strings briefly exist in editable Compose/IME memory; callers must
 * immediately copy and clear the ephemeral CharArray passed to onConfirm.
 */
@Composable
internal fun EncryptedPasswordDialog(
    mode: EncryptedPasswordMode,
    busy: Boolean,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember(mode) { mutableStateOf("") }
    var repeated by remember(mode) { mutableStateOf("") }
    val exporting = mode == EncryptedPasswordMode.EXPORT
    val valid = password.length in 12..1024 && (!exporting || repeated == password)

    fun dismiss() {
        password = ""
        repeated = ""
        onDismiss()
    }

    AlertDialog(
        // Prevent Android screenshots/screen recording of the password prompt.
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        onDismissRequest = ::dismiss,
        title = { Text(stringResource(if (exporting) R.string.aes_password_export_title else R.string.aes_password_import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(if (exporting) R.string.aes_password_export_notice else R.string.aes_password_import_notice),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= 1024) password = it },
                    label = { Text(stringResource(R.string.aes_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("aes-password")
                )
                if (exporting) {
                    OutlinedTextField(
                        value = repeated,
                        onValueChange = { if (it.length <= 1024) repeated = it },
                        label = { Text(stringResource(R.string.aes_password_confirm)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("aes-password-repeat")
                    )
                    if (repeated.isNotEmpty() && repeated != password) {
                        Text(
                            stringResource(R.string.aes_password_mismatch),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text(
                    stringResource(R.string.aes_password_security_notice),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val secret = password.toCharArray()
                password = ""
                repeated = ""
                try {
                    onConfirm(secret)
                } finally {
                    secret.fill('\u0000')
                }
            }, enabled = valid && !busy,
                modifier = Modifier.testTag("aes-password-continue")) {
                Text(stringResource(if (exporting) R.string.aes_password_choose_location else R.string.aes_password_verify))
            }
        },
        dismissButton = {
            TextButton(onClick = ::dismiss, modifier = Modifier.testTag("aes-password-cancel")) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
