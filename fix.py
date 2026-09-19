import sys
with open('app/src/main/java/com/duta/movie/ui/SettingsScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('@Composable\n@Composable\nfun DisplayAdjustmentSlider', '@Composable\nfun DisplayAdjustmentSlider')
if 'import androidx.compose.foundation.shape.CircleShape' not in text:
    text = text.replace('import androidx.compose.foundation.shape.RoundedCornerShape', 'import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.foundation.shape.CircleShape')

with open('app/src/main/java/com/duta/movie/ui/SettingsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
