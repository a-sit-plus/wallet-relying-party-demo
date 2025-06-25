package at.asit.apps.terminal_sp.prototype.server

import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration.Companion.minutes

/**
 * Holds a list of all successfully authenticated users.
 */
@OptIn(InternalAPI::class)
@Service
class UserStore {
    private val lifetime = 5.minutes
    private val lock = ReentrantLock()
    private val entries: MutableList<UserStoreEntry> = mutableListOf()

    fun listAllUsers(): List<OpenId4VpPrincipal> = entries.map { it.user }

    fun loadUser(id: String): OpenId4VpPrincipal? = entries.firstOrNull { it.id == id }?.user

    fun removeUser(id: String): OpenId4VpPrincipal? = lock.withLock {
        removeExpiredEntries()
        val entry = entries.firstOrNull { it.id == id }
        if (entry != null) {
            entries.remove(entry)
        }
        return entry?.user
    }

    fun put(id: String, user: OpenId4VpPrincipal): Boolean? = lock.withLock {
        removeExpiredEntries()
        entries.add(UserStoreEntry(id, user, Clock.System.now().plus(lifetime)))
    }

    private fun removeExpiredEntries() {
        entries.removeAll { it.notAfter < Clock.System.now() }
    }

}


data class UserStoreEntry(val id: String, val user: OpenId4VpPrincipal, val notAfter: Instant)

