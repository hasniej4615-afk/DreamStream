package com.duta.movie.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable
    data object Home : Destination
    
    @Serializable
    data object Movies : Destination
    
    @Serializable
    data object TVShows : Destination
    
    @Serializable
    data object MyList : Destination

    @Serializable
    data class VideoDetail(val videoId: String) : Destination

    @Serializable
    data class CategoryResults(val path: String, val name: String) : Destination

    @Serializable
    data class ActressProfile(val path: String, val name: String) : Destination

    @Serializable
    data class Player(val videoId: String, val serverUrl: String? = null) : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object RepoManager : Destination
}
