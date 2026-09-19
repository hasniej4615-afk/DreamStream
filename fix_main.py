import os

path = 'app/src/main/java/com/duta/movie/MainActivity.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('Triple(Destination.Home, Icons.Default.Home, "Home")', 'Triple(Destination.Home, Icons.Default.Home, stringResource(R.string.home))')
content = content.replace('Triple(Destination.Movies, Icons.Default.Movie, "Movies")', 'Triple(Destination.Movies, Icons.Default.Movie, stringResource(R.string.movies))')
content = content.replace('Triple(Destination.TVShows, Icons.Default.Tv, "TV Shows")', 'Triple(Destination.TVShows, Icons.Default.Tv, stringResource(R.string.tv_shows))')
content = content.replace('Triple(Destination.MyList, Icons.Default.Bookmark, "My List")', 'Triple(Destination.MyList, Icons.Default.Bookmark, stringResource(R.string.my_list))')

# Ensure import stringResource is present
if 'import androidx.compose.ui.res.stringResource' not in content:
    content = content.replace('import androidx.compose.runtime.*', 'import androidx.compose.runtime.*\nimport androidx.compose.ui.res.stringResource\nimport com.duta.movie.R')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

