package com.example.questly.backend.auth

import com.example.questly.backend.db.EmailTokens
import com.example.questly.backend.db.RefreshTokens
import com.example.questly.backend.db.Users
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

private const val VERIFY_TTL_HOURS = 24L
private const val REFRESH_TTL_DAYS = 30L
private const val PURPOSE_VERIFY = "VERIFY"

/** Core auth logic: registration, email verification, sign-in, and refresh-token rotation. */
class AuthService(
    private val jwt: JwtConfig,
    private val email: EmailSender,
) {
    private val random = SecureRandom()

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    private fun secureToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun normalizeEmail(raw: String) = raw.trim().lowercase()

    /**
     * Creates an inactive account and emails a verification token. Always succeeds silently even if
     * the email is taken, so the endpoint can't be used to enumerate registered addresses.
     */
    suspend fun register(req: RegisterRequest) {
        val email = normalizeEmail(req.email)
        if (req.password.length < 8) throw ApiException(HttpStatusCode.BadRequest, "weak_password", "Password must be at least 8 characters")
        if (req.displayName.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "invalid_name", "Display name is required")

        val token = newSuspendedTransaction(Dispatchers.IO) {
            val exists = Users.selectAll().where { Users.email eq email }.limit(1).any()
            if (exists) return@newSuspendedTransaction null

            val userId = UUID.randomUUID()
            Users.insert {
                it[id] = userId
                it[Users.email] = email
                it[displayName] = req.displayName.trim()
                it[passwordHash] = Passwords.hash(req.password)
                it[emailVerified] = false
                it[createdAt] = now()
            }
            val verifyToken = secureToken()
            EmailTokens.insert {
                it[EmailTokens.token] = verifyToken
                it[EmailTokens.userId] = userId
                it[purpose] = PURPOSE_VERIFY
                it[expiresAt] = now().plusHours(VERIFY_TTL_HOURS)
                it[usedAt] = null
            }
            verifyToken
        }
        if (token != null) this.email.sendVerification(email, token)
    }

    /** Consumes a verification token, activates the account, and returns a fresh token pair. */
    suspend fun verifyEmail(req: VerifyEmailRequest): TokenPair = newSuspendedTransaction(Dispatchers.IO) {
        val row = EmailTokens.selectAll()
            .where { (EmailTokens.token eq req.token) and (EmailTokens.purpose eq PURPOSE_VERIFY) }
            .limit(1)
            .firstOrNull()
            ?: throw ApiException(HttpStatusCode.Gone, "invalid_token", "Verification link is invalid")

        if (row[EmailTokens.usedAt] != null || row[EmailTokens.expiresAt].isBefore(now())) {
            throw ApiException(HttpStatusCode.Gone, "expired_token", "Verification link has expired")
        }
        val userId = row[EmailTokens.userId]
        EmailTokens.update({ EmailTokens.token eq req.token }) { it[usedAt] = now() }
        Users.update({ Users.id eq userId }) { it[emailVerified] = true }

        val userEmail = Users.selectAll().where { Users.id eq userId }.first()[Users.email]
        issueTokens(userId, userEmail)
    }

    suspend fun login(req: LoginRequest): TokenPair = newSuspendedTransaction(Dispatchers.IO) {
        val email = normalizeEmail(req.email)
        val user = Users.selectAll().where { Users.email eq email }.limit(1).firstOrNull()
            ?: throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Email or password is incorrect")

        val hash = user[Users.passwordHash]
            ?: throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Email or password is incorrect")
        if (!Passwords.verify(req.password, hash)) {
            throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Email or password is incorrect")
        }
        if (!user[Users.emailVerified]) {
            throw ApiException(HttpStatusCode.Forbidden, "email_unverified", "Please verify your email first")
        }
        issueTokens(user[Users.id], user[Users.email])
    }

    /** Rotates the refresh token: the presented one is revoked and a new pair is issued. */
    suspend fun refresh(req: RefreshRequest): TokenPair = newSuspendedTransaction(Dispatchers.IO) {
        val row = RefreshTokens.selectAll().where { RefreshTokens.token eq req.refreshToken }.limit(1).firstOrNull()
            ?: throw ApiException(HttpStatusCode.Unauthorized, "invalid_token", "Invalid refresh token")
        if (row[RefreshTokens.revokedAt] != null || row[RefreshTokens.expiresAt].isBefore(now())) {
            throw ApiException(HttpStatusCode.Unauthorized, "invalid_token", "Refresh token expired")
        }
        val userId = row[RefreshTokens.userId]
        RefreshTokens.update({ RefreshTokens.token eq req.refreshToken }) { it[revokedAt] = now() }
        val userEmail = Users.selectAll().where { Users.id eq userId }.first()[Users.email]
        issueTokens(userId, userEmail)
    }

    suspend fun logout(req: LogoutRequest) {
        newSuspendedTransaction(Dispatchers.IO) {
            RefreshTokens.update({ RefreshTokens.token eq req.refreshToken }) { it[revokedAt] = now() }
        }
    }

    suspend fun getUser(userId: UUID): UserDto = newSuspendedTransaction(Dispatchers.IO) {
        val u = Users.selectAll().where { Users.id eq userId }.limit(1).firstOrNull()
            ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Account not found")
        UserDto(
            id = u[Users.id].toString(),
            email = u[Users.email],
            displayName = u[Users.displayName],
            emailVerified = u[Users.emailVerified],
            createdAt = u[Users.createdAt].toString(),
        )
    }

    suspend fun deleteUser(userId: UUID) {
        newSuspendedTransaction(Dispatchers.IO) {
            // FK cascades remove email_tokens and refresh_tokens.
            Users.deleteWhere { Users.id eq userId }
        }
    }

    private fun issueTokens(userId: UUID, email: String): TokenPair {
        val refresh = secureToken()
        RefreshTokens.insert {
            it[token] = refresh
            it[RefreshTokens.userId] = userId
            it[expiresAt] = now().plusDays(REFRESH_TTL_DAYS)
            it[revokedAt] = null
        }
        return TokenPair(
            accessToken = jwt.issueAccessToken(userId, email),
            refreshToken = refresh,
            expiresInSeconds = jwt.accessTtlSeconds,
        )
    }
}
