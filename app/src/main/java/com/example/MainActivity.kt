package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.ConnectionStatus
import com.example.ui.SmartShoeViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SmartShoeApp()
            }
        }
    }
}

@Composable
fun SmartShoeApp(viewModel: SmartShoeViewModel = viewModel()) {
    val screen by viewModel.screen.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (screen) {
            is AppScreen.Start -> StartScreen(viewModel)
            is AppScreen.Dashboard -> {
                DashboardScreen(viewModel)
                BackHandler {
                    viewModel.disconnect()
                }
            }
        }
    }
}

@Composable
fun StartScreen(viewModel: SmartShoeViewModel) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Smart Shoe",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Every step counts.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Icon/Illustration
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.connect() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = connectionStatus != ConnectionStatus.Connecting
        ) {
            if (connectionStatus == ConnectionStatus.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = "Start", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Connection status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (connectionStatus) {
                            is ConnectionStatus.Connected -> Color(0xFF4CAF50)
                            is ConnectionStatus.Connecting -> Color(0xFFFFC107)
                            is ConnectionStatus.Error -> Color(0xFFF44336)
                            else -> Color.Gray
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (connectionStatus) {
                    is ConnectionStatus.Connected -> "Connected"
                    is ConnectionStatus.Connecting -> "Connecting..."
                    is ConnectionStatus.Error -> "Connection failed"
                    else -> "Not Connected"
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (connectionStatus is ConnectionStatus.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (connectionStatus as ConnectionStatus.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun DashboardScreen(viewModel: SmartShoeViewModel) {
    val stepData by viewModel.stepData.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isResetting by viewModel.isResetting.collectAsState()
    val showTargetReached by viewModel.showTargetReachedDialog.collectAsState()

    var showTargetDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                Spacer(modifier = Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Disconnect")
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connectionStatus == ConnectionStatus.Connected) Color(0xFF4CAF50) else Color(0xFFF44336)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (connectionStatus == ConnectionStatus.Connected) "Connected" else "Reconnecting...",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    IconButton(onClick = { showTargetDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Set Target")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step Counter Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Steps Taken",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${stepData?.steps ?: 0}",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Progress Section
            val currentSteps = stepData?.steps ?: 0
            val targetSteps = stepData?.target ?: 1000
            val progress = if (targetSteps > 0) currentSteps.toFloat() / targetSteps.toFloat() else 0f
            val remaining = (targetSteps - currentSteps).coerceAtLeast(0)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$currentSteps / $targetSteps steps",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$remaining remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { showResetConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = !isResetting
            ) {
                if (isResetting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Reset Steps")
                }
            }
        }
    }

    // Dialogs
    if (showTargetDialog) {
        SetTargetDialog(
            currentTarget = stepData?.target ?: 1000,
            onDismiss = { showTargetDialog = false },
            onSave = { newTarget ->
                viewModel.updateTarget(
                    newTarget,
                    onSuccess = {
                        showTargetDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Target updated!") }
                    },
                    onError = { error ->
                        scope.launch { snackbarHostState.showSnackbar(error) }
                    }
                )
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Steps") },
            text = { Text("Are you sure you want to reset your steps to zero?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetSteps(
                            onSuccess = {
                                scope.launch { snackbarHostState.showSnackbar("Steps reset successfully.") }
                            },
                            onError = { error ->
                                scope.launch { snackbarHostState.showSnackbar(error) }
                            }
                        )
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTargetReached) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTargetDialog() },
            title = { Text("Target Completed!") },
            text = { Text("Congratulations! You've reached your step goal for today.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissTargetDialog() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SetTargetDialog(currentTarget: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var targetValue by remember { mutableStateOf(currentTarget.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set Step Target",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { if (it.all { char -> char.isDigit() }) targetValue = it },
                    label = { Text("Steps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val target = targetValue.toIntOrNull() ?: 0
                        onSave(target)
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
