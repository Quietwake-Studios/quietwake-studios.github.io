package com.tavoohoh.quiet.ui

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.leaderboard.LeaderboardScore
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Task
import com.tavoohoh.quiet.core.LeaderboardEntry
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optional Google Play Games layer, shared by the family (port of QuietUI's `GameCenterService`).
 * Everything works signed out and offline; the app's local data is the source of truth and is
 * pushed here when the player is signed in. Play Games queues achievements and scores sent
 * offline and delivers them when the device reconnects.
 *
 * Not configured (`appID` empty or a placeholder, e.g. before the game exists in Play Console):
 * the SDK is never initialized and the service stays signed out. The app's manifest removes
 * Play's automatic init provider so this is the only place that starts it.
 *
 * Nothing read from Play Games is stored: names and scores are only shown.
 */
class PlayGamesService(private val appID: String) {
    /** The ID looks like a real Play Console project number. */
    val isConfigured: Boolean = appID.isNotBlank() && appID.all(Char::isDigit) && appID.trimStart('0').isNotEmpty()

    var isAuthenticated by mutableStateOf(false)
        private set
    /** The signed-in player's name, for Settings. Shown only, never stored. */
    var playerName: String? by mutableStateOf(null)
        private set
    private var playerID: String? = null

    /** Called each time the player becomes signed in, to sync pending data. */
    var onAuthenticated: () -> Unit = {}

    /** The visible activity (Play Games v2 clients need one). Set by the app's activity. */
    private var activityRef = WeakReference<Activity>(null)
    private val activity: Activity? get() = activityRef.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    fun initialize(application: Application) {
        if (isConfigured) runCatching { PlayGamesSdk.initialize(application) }.onFailure { note("init failed: $it") }
    }

    /** The activity came to the front: Play's automatic sign-in result is read again. */
    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
        refresh()
    }

    fun refresh() {
        val activity = activity ?: return
        if (!isConfigured) return
        runCatching {
            PlayGames.getGamesSignInClient(activity).isAuthenticated().addOnCompleteListener { task ->
                update(task.isSuccessful && task.result?.isAuthenticated == true)
            }
        }
    }

    /** The Settings row and the Leaderboards card: Play Games' own sign-in. */
    fun signIn() {
        val activity = activity ?: return
        if (!isConfigured) return
        runCatching {
            PlayGames.getGamesSignInClient(activity).signIn().addOnCompleteListener { task ->
                update(task.isSuccessful && task.result?.isAuthenticated == true)
            }
        }
    }

    private fun update(authenticated: Boolean) {
        val became = authenticated && !isAuthenticated
        isAuthenticated = authenticated
        if (!authenticated) {
            playerName = null
            playerID = null
            return
        }
        val activity = activity ?: return
        PlayGames.getPlayersClient(activity).currentPlayer.addOnSuccessListener { player ->
            playerName = player.displayName
            playerID = player.playerId
        }
        if (became) onAuthenticated()
    }

    /** Play Games' achievements screen (iOS: the Game Center dashboard). */
    fun showAchievements() {
        val activity = activity ?: return
        if (!isAuthenticated) return
        PlayGames.getAchievementsClient(activity).achievementsIntent.addOnSuccessListener { activity.startActivityForResult(it, 9001) }
    }

    // Achievements and scores

    fun unlock(id: String) {
        val activity = activity ?: return
        if (isAuthenticated && id.isReal()) runCatching { PlayGames.getAchievementsClient(activity).unlock(id) }
    }

    /** Incremental achievements: Play keeps the highest steps it has seen. */
    fun setSteps(id: String, steps: Int) {
        val activity = activity ?: return
        if (isAuthenticated && id.isReal() && steps > 0) runCatching { PlayGames.getAchievementsClient(activity).setSteps(id, steps) }
    }

    /** `tag`: what the app sends with the score (the daily's day and time, `ScoreTag`). */
    fun submit(id: String, score: Long, tag: String? = null) {
        val activity = activity ?: return
        if (!isAuthenticated || !id.isReal()) return
        runCatching {
            val client = PlayGames.getLeaderboardsClient(activity)
            if (tag != null) client.submitScore(id, score, tag) else client.submitScore(id, score)
            note("submitted $score to $id")
        }
    }

    /**
     * A page of a board: `daily` loads Play's daily span (the app then keeps only today's
     * tagged results, `Leaderboards.today`), otherwise all time. The local player's entry, if
     * they have one, comes apart as on iOS.
     */
    suspend fun entries(id: String, friendsOnly: Boolean, daily: Boolean): Pair<LeaderboardEntry?, List<LeaderboardEntry>> {
        val activity = activity ?: throw IllegalStateException("no activity")
        if (!isAuthenticated || !id.isReal()) return null to emptyList()
        val span = if (daily) LeaderboardVariant.TIME_SPAN_DAILY else LeaderboardVariant.TIME_SPAN_ALL_TIME
        val collection = if (friendsOnly) LeaderboardVariant.COLLECTION_FRIENDS else LeaderboardVariant.COLLECTION_PUBLIC
        val data = PlayGames.getLeaderboardsClient(activity).loadTopScores(id, span, collection, 25, true).await()
        val scores = data.get() ?: return null to emptyList()
        try {
            val entries = scores.scores.map { it.toEntry() }
            return entries.firstOrNull { it.isLocalPlayer } to entries
        } finally {
            scores.release()
        }
    }

    private fun LeaderboardScore.toEntry() = LeaderboardEntry(
        id = scoreHolder?.playerId ?: scoreHolderDisplayName,
        rank = rank.toInt(),
        name = scoreHolderDisplayName,
        score = rawScore,
        isLocalPlayer = scoreHolder?.playerId != null && scoreHolder?.playerId == playerID,
        tag = scoreTag.orEmpty(),
    )

    // Saved games

    /**
     * The saved game's bytes. Conflicting copies are combined with `merge` (keep the most
     * progress) and written back as one, like iOS's `fetchSnapshot`.
     */
    suspend fun loadSnapshot(name: String, merge: (ByteArray, ByteArray) -> ByteArray): ByteArray? {
        val activity = activity ?: return null
        if (!isAuthenticated) return null
        val client = PlayGames.getSnapshotsClient(activity)
        var result = client.open(name, true, SnapshotsClient.RESOLUTION_POLICY_MANUAL).await()
        var guard = 0
        while (result.isConflict && guard++ < 5) {
            val conflict = result.conflict ?: break
            val mine = conflict.snapshot.snapshotContents.readFully()
            val theirs = conflict.conflictingSnapshot.snapshotContents.readFully()
            val merged = merge(mine, theirs)
            conflict.resolutionSnapshotContents.writeBytes(merged)
            result = client.resolveConflict(conflict.conflictId, conflict.snapshot.metadata.snapshotId, SnapshotMetadataChange.EMPTY_CHANGE, conflict.resolutionSnapshotContents).await()
        }
        val snapshot = result.data ?: return null
        val bytes = snapshot.snapshotContents.readFully()
        client.discardAndClose(snapshot)
        return bytes.takeIf { it.isNotEmpty() }
    }

    suspend fun saveSnapshot(name: String, bytes: ByteArray) {
        val activity = activity ?: return
        if (!isAuthenticated) return
        val client = PlayGames.getSnapshotsClient(activity)
        val result = client.open(name, true, SnapshotsClient.RESOLUTION_POLICY_HIGHEST_PROGRESS).await()
        val snapshot = result.data ?: return
        snapshot.snapshotContents.writeBytes(bytes)
        client.commitAndClose(snapshot, SnapshotMetadataChange.EMPTY_CHANGE).await()
    }

    /**
     * Deletes the saved game ("Reset progress"). Play Games can't reset achievements or
     * withdraw scores from a client app; they stay in the player's Play Games profile.
     */
    suspend fun deleteSnapshot(name: String): Boolean {
        val activity = activity ?: return false
        if (!isAuthenticated) return false
        return runCatching {
            val client = PlayGames.getSnapshotsClient(activity)
            val snapshot = client.open(name, false).await().data ?: return true
            val metadata = snapshot.metadata.freeze()
            client.discardAndClose(snapshot)
            client.delete(metadata).await()
            true
        }.getOrDefault(false)
    }

    private fun String.isReal() = isNotBlank() && !startsWith("TODO")

    private fun note(message: String) = Log.i("PlayGames", message)
}

/** A Play services task as a suspending call (no extra dependency). */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error != null) continuation.resumeWithException(error)
        else if (task.isCanceled) continuation.cancel()
        else continuation.resume(task.result)
    }
}
