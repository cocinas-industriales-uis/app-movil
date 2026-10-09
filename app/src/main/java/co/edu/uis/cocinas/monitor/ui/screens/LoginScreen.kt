package co.edu.uis.cocinas.monitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.edu.uis.cocinas.monitor.BuildConfig
import co.edu.uis.cocinas.monitor.ui.LoginUiState

@Composable
fun LoginScreen(state: LoginUiState, onLogin: (String, String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MONITOREO DE COCINAS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Seguridad industrial · UIS", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(32.dp))
        OutlinedTextField(
            value = user, onValueChange = { user = it }, label = { Text("Usuario o correo") },
            singleLine = true, enabled = !state.loading, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, label = { Text("Contraseña") },
            singleLine = true, enabled = !state.loading, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (state.error != null) {
            Spacer(Modifier.size(12.dp))
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.size(20.dp))
        Button(onClick = { onLogin(user, pass) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("INICIAR SESIÓN")
        }
        if (BuildConfig.USE_MOCK) {
            Spacer(Modifier.size(24.dp))
            Text(
                "Modo simulado (debug): usuario \"admin\" u \"operador\", contraseña de 4+ caracteres.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
