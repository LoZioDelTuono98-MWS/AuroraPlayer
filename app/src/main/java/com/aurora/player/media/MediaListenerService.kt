package com.aurora.player.media

import android.service.notification.NotificationListenerService

/**
 * Non legge le notifiche: esiste solo perché Android concede l'accesso alle
 * sessioni multimediali (Spotify, SimpMusic, YouTube Music, player locali...)
 * alle app che hanno un "listener notifiche" abilitato.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NowPlaying.attach(this)
    }
}
