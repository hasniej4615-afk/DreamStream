import os
path = 'app/src/main/java/com/duta/movie/ui/PlayerControls.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old_block = '''                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                                .onFocusChanged { isSliderFocused = it.isFocused }
                                .scale(if (isSliderFocused) 1.02f else 1f),'''

new_block = '''                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                                .onFocusChanged { isSliderFocused = it.isFocused }
                                .scale(if (isSliderFocused) 1.02f else 1f)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == androidx.compose.ui.input.key.Key.DirectionRight || event.key == androidx.compose.ui.input.key.Key.DirectionLeft) {
                                        if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                            val step = if (duration > 0) 10000f / duration else 0.05f
                                            val current = sliderPosition ?: if (duration > 0) currentPosition.toFloat() / duration else 0f
                                            if (event.key == androidx.compose.ui.input.key.Key.DirectionRight) {
                                                sliderPosition = (current + step).coerceIn(0f, 1f)
                                            } else {
                                                sliderPosition = (current - step).coerceIn(0f, 1f)
                                            }
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },'''

if old_block in content:
    content = content.replace(old_block, new_block)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Success")
else:
    print("Failed to find old block")
