package io.github.lihuihnu.xuaneynote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Password examples are synthetic; do not use or print real user credentials. */
@RunWith(AndroidJUnit4::class)
class EncryptedPasswordDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exportingRequiresMatchingPasswordAndCancelNeverConfirms() {
        var called = 0
        var canceled = 0
        var received: String? = null
        compose.setContent {
            MaterialTheme {
                EncryptedPasswordDialog(
                    mode = EncryptedPasswordMode.EXPORT, busy = false,
                    onConfirm = { value ->
                        called++
                        received = String(value)
                    },
                    onDismiss = { canceled++ }
                )
            }
        }
        compose.onNodeWithTag("aes-password-continue").assertIsNotEnabled()
        compose.onNodeWithTag("aes-password").performTextReplacement("test-too-short")
        compose.onNodeWithTag("aes-password-repeat").performTextReplacement("mismatch-test-value")
        compose.onNodeWithTag("aes-password-continue").assertIsNotEnabled()
        compose.onNodeWithTag("aes-password").performTextReplacement("Long-synthetic-2026-🧪")
        compose.onNodeWithTag("aes-password-repeat").performTextReplacement("Long-synthetic-2026-🧪")
        compose.onNodeWithTag("aes-password-continue").assertIsEnabled().performClick()
        assertEquals(1, called)
        assertEquals("Long-synthetic-2026-🧪", received)
        // After confirmation, password cannot be resubmitted accidentally.
        compose.onNodeWithTag("aes-password-continue").assertIsNotEnabled()
        compose.onNodeWithTag("aes-password-cancel").performClick()
        assertEquals(1, canceled)
        assertEquals(1, called)
    }

    @Test fun importingOnlyAsksOnePasswordAndCancelIsNonDestructive() {
        var verified = 0
        var canceled = 0
        compose.setContent {
            MaterialTheme {
                EncryptedPasswordDialog(
                    mode = EncryptedPasswordMode.IMPORT, busy = false,
                    onConfirm = { verified++ },
                    onDismiss = { canceled++ }
                )
            }
        }
        compose.onNodeWithTag("aes-password-repeat").assertDoesNotExist()
        compose.onNodeWithTag("aes-password-continue").assertIsNotEnabled()
        compose.onNodeWithTag("aes-password").performTextReplacement("Unicode-pass-2026-🧪")
        compose.onNodeWithTag("aes-password-continue").assertIsEnabled()
        compose.onNodeWithTag("aes-password-cancel").performClick()
        assertEquals(0, verified)
        assertEquals(1, canceled)
    }
}
