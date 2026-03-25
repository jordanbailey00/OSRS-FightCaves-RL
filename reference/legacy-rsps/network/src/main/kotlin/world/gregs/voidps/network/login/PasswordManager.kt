package world.gregs.voidps.network.login

import com.github.michaelbull.logging.InlineLogger
import org.mindrot.jbcrypt.BCrypt
import world.gregs.voidps.network.Response

/**
 * Checks account credentials are valid
 */
class PasswordManager(private val account: AccountLoader) {
    private val logger = InlineLogger()

    fun validate(username: String, password: String): Int {
        val exists = account.exists(username)
        if (username.length > 12) {
            log(username, exists, passwordHash = null, Response.LOGIN_SERVER_REJECTED_SESSION)
            return Response.LOGIN_SERVER_REJECTED_SESSION
        }
        val passwordHash = account.password(username)
        if (!exists) {
            if (passwordHash != null) {
                // Failed to find account file despite AccountDefinition exists in memory (aka existed on startup)
                log(username, exists, passwordHash, Response.ACCOUNT_DISABLED)
                return Response.ACCOUNT_DISABLED
            }
            log(username, exists, passwordHash, Response.SUCCESS)
            return Response.SUCCESS
        }
        try {
            if (passwordHash == null) {
                // Failed to find accounts password despite account file existing (created since startup)
                log(username, exists, passwordHash, Response.ACCOUNT_DISABLED)
                return Response.ACCOUNT_DISABLED
            }
            if (BCrypt.checkpw(password, passwordHash)) {
                log(username, exists, passwordHash, Response.SUCCESS)
                return Response.SUCCESS
            }
        } catch (e: IllegalArgumentException) {
            log(username, exists, passwordHash, Response.COULD_NOT_COMPLETE_LOGIN)
            return Response.COULD_NOT_COMPLETE_LOGIN
        }
        log(username, exists, passwordHash, Response.INVALID_CREDENTIALS)
        return Response.INVALID_CREDENTIALS
    }

    fun encrypt(username: String, password: String): String {
        val passwordHash = account.password(username)
        if (passwordHash != null) {
            return passwordHash
        }
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun log(username: String, exists: Boolean, passwordHash: String?, response: Int) {
        logger.info {
            "login_validate username=$username exists=$exists password_hash_present=${passwordHash != null} response=${responseName(response)}($response)"
        }
    }

    private fun responseName(response: Int): String = when (response) {
        Response.DATA_CHANGE -> "DATA_CHANGE"
        Response.VIDEO_AD -> "VIDEO_AD"
        Response.SUCCESS -> "SUCCESS"
        Response.INVALID_CREDENTIALS -> "INVALID_CREDENTIALS"
        Response.ACCOUNT_DISABLED -> "ACCOUNT_DISABLED"
        Response.ACCOUNT_ONLINE -> "ACCOUNT_ONLINE"
        Response.GAME_UPDATE -> "GAME_UPDATE"
        Response.WORLD_FULL -> "WORLD_FULL"
        Response.LOGIN_SERVER_OFFLINE -> "LOGIN_SERVER_OFFLINE"
        Response.LOGIN_LIMIT_EXCEEDED -> "LOGIN_LIMIT_EXCEEDED"
        Response.BAD_SESSION_ID -> "BAD_SESSION_ID"
        Response.LOGIN_SERVER_REJECTED_SESSION -> "LOGIN_SERVER_REJECTED_SESSION"
        Response.MEMBERS_ACCOUNT_REQUIRED -> "MEMBERS_ACCOUNT_REQUIRED"
        Response.COULD_NOT_COMPLETE_LOGIN -> "COULD_NOT_COMPLETE_LOGIN"
        Response.SERVER_BEING_UPDATED -> "SERVER_BEING_UPDATED"
        Response.RECONNECTING -> "RECONNECTING"
        Response.LOGIN_ATTEMPTS_EXCEEDED -> "LOGIN_ATTEMPTS_EXCEEDED"
        Response.MEMBERS_ONLY_AREA -> "MEMBERS_ONLY_AREA"
        Response.INVALID_LOGIN_SERVER -> "INVALID_LOGIN_SERVER"
        Response.TRANSFERRING_PROFILE -> "TRANSFERRING_PROFILE"
        else -> "UNKNOWN"
    }
}
