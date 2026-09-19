import re

with open('C:/Users/User/Desktop/Duta/app/src/main/java/com/duta/movie/ui/VideoViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target = """                                    if (result != null) {
                                        val winUrl = result.videoUrl
                                        
                                        // Only reject truly invalid results"""

replacement = """                                    if (result != null) {
                                        val winUrl = result.videoUrl
                                        val winHost = try { android.net.Uri.parse(winUrl).host?.lowercase() } catch(_: Exception) { null }
                                        
                                        if (winHost != null && deadMirrors.contains(winHost)) {
                                            android.util.Log.w("VideoViewModel", "God Mode rejected $winUrl because host is blacklisted.")
                                            return@launch
                                        }
                                        
                                        // Only reject truly invalid results"""

content = content.replace(target, replacement)

with open('C:/Users/User/Desktop/Duta/app/src/main/java/com/duta/movie/ui/VideoViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
