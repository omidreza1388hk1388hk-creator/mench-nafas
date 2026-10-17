package com.omidgame.mench.feature.people.data

import android.content.Context
import com.omidgame.mench.core.network.BlockBody
import com.omidgame.mench.core.network.BlockedUserResponse
import com.omidgame.mench.core.network.DeleteAccountBody
import com.omidgame.mench.core.network.ProductApi
import com.omidgame.mench.core.network.PublicUserResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A person as a stranger may see them: never carries a phone number. */
data class PublicUser(
    val id: String,
    val displayName: String?,
    val username: String?,
    val bio: String?,
    val avatarVersion: Long?,
)

sealed interface PeopleResult<out T> {
    data class Success<T>(val value: T) : PeopleResult<T>
    data object NotFound : PeopleResult<Nothing>
    data object Offline : PeopleResult<Nothing>
    data object Failed : PeopleResult<Nothing>
}

private val INVITE_CODE_REGEX = Regex("^[A-Z0-9]{8}$")

/**
 * Accepts a bare code ("ABCD2345") or a link ("mench://invite/ABCD2345") and
 * returns the normalised code, or null when the text is neither.
 */
fun extractInviteCode(raw: String): String? {
    val tail = raw.trim().substringAfterLast('/').substringBefore('?').uppercase()
    return tail.takeIf { INVITE_CODE_REGEX.matches(it) }
}

fun inviteLink(code: String): String = "mench://invite/$code"

/** Finding people, blocking, and account lifecycle calls. A concrete @Singleton, so Hilt needs no binding module. */
@Singleton
class PeopleRepository @Inject constructor(
    private val api: ProductApi,
    @ApplicationContext private val context: Context,
) {
    suspend fun findByUsername(raw: String): PeopleResult<PublicUser> {
        val username = raw.trim().removePrefix("@")
        if (username.isEmpty()) return PeopleResult.NotFound
        return call { api.byUsername(username).toPublic() }
    }

    suspend fun resolveInvite(raw: String): PeopleResult<PublicUser> {
        val code = extractInviteCode(raw) ?: return PeopleResult.NotFound
        return call { api.resolveInvite(code).toPublic() }
    }

    suspend fun myInviteCode(): PeopleResult<String> = call { api.myInvite().code }

    suspend fun blocked(): PeopleResult<List<PublicUser>> = call { api.blocked().map { it.toPublic() } }

    suspend fun block(userId: String): PeopleResult<Unit> = call { api.block(BlockBody(userId)) }

    suspend fun unblock(userId: String): PeopleResult<Unit> = call { api.unblock(userId) }

    /** Streams the JSON export into the cache dir (covered by FileProvider's "exports/" path) and returns the file. */
    suspend fun exportMyData(): PeopleResult<File> = call {
        val body = api.exportData()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "mench-my-data.json")
        body.use { response ->
            response.byteStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        file
    }

    suspend fun deleteAccount(): PeopleResult<Unit> = call { api.deleteAccount(DeleteAccountBody("DELETE")) }

    private suspend fun <T> call(block: suspend () -> T): PeopleResult<T> = withContext(Dispatchers.IO) {
        try {
            PeopleResult.Success(block())
        } catch (e: IOException) {
            PeopleResult.Offline
        } catch (e: HttpException) {
            if (e.code() == 404 || e.code() == 400) PeopleResult.NotFound else PeopleResult.Failed
        }
    }

    private fun BlockedUserResponse.toPublic() = PublicUser(
        id = id,
        displayName = displayName,
        username = username,
        bio = bio,
        avatarVersion = avatarVersion,
    )

    private fun PublicUserResponse.toPublic() = PublicUser(
        id = id,
        displayName = displayName,
        username = username,
        bio = bio,
        avatarVersion = avatarVersion,
    )
}
