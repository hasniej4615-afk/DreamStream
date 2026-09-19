import os

path = 'app/src/main/java/com/duta/movie/ui/VideoListScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

orig_str = '''                                    LaunchedEffect(path) { 
                                        if (rowVideos.isEmpty() && (!isImmersiveMode || index <= 2)) {
                                            viewModel.fetchVideosForCategoryRow(path)
                                        }
                                    }'''

new_str = '''                                    LaunchedEffect(path) { 
                                        if (rowVideos.isEmpty()) {
                                            viewModel.fetchVideosForCategoryRow(path)
                                        }
                                    }'''

content = content.replace(orig_str, new_str)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
