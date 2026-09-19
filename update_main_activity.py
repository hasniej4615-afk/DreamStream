import os

path = 'app/src/main/java/com/duta/movie/MainActivity.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add imports
imports = '''import androidx.compose.ui.draw.scale
import com.duta.movie.util.UpdateChecker
import com.duta.movie.util.UpdateInfo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext'''

content = content.replace('import androidx.compose.ui.draw.scale', imports)

# Add logic in setContent
set_content_old = '''        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isInPip by isPipMode
            MovieTheme {'''

set_content_new = '''        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isInPip by isPipMode
            
            val context = LocalContext.current
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            
            LaunchedEffect(Unit) {
                val info = UpdateChecker.checkForUpdate()
                if (info != null && info.latestVersionCode > BuildConfig.VERSION_CODE) {
                    updateInfo = info
                }
            }

            MovieTheme {
                if (updateInfo != null) {
                    AlertDialog(
                        onDismissRequest = { updateInfo = null },
                        title = { Text("Update Available", color = Color.White) },
                        text = {
                            Column {
                                Text("Version  is now available!", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(updateInfo!!.changelog, color = Color.LightGray)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { 
                                    UpdateChecker.openTelegram(context, updateInfo!!.telegramUrl)
                                    updateInfo = null 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("Update Now", color = Color.White)
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = { updateInfo = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("Later", color = Color.White)
                            }
                        },
                        containerColor = Color(0xFF1E1E1E)
                    )
                }
'''

content = content.replace(set_content_old, set_content_new)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
