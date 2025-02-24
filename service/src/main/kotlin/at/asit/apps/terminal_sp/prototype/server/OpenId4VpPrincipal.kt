package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult.IdToken
import at.asitplus.wallet.lib.openid.AuthnResponseResult.Success
import at.asitplus.wallet.lib.openid.AuthnResponseResult.SuccessIso
import at.asitplus.wallet.lib.openid.AuthnResponseResult.SuccessSdJwt
import at.asitplus.wallet.lib.openid.AuthnResponseResult.ValidationError
import at.asitplus.wallet.lib.openid.AuthnResponseResult.VerifiableDCQLPresentationValidationResults
import at.asitplus.wallet.lib.openid.AuthnResponseResult.VerifiablePresentationValidationResults
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest
import java.time.Instant

/** Adapter from [AuthenticatedUser] to [AuthenticatedPrincipal] */
@Serializable
class OpenId4VpPrincipal(
    val authenticatedUser: AuthenticatedUser,
) : AuthenticatedPrincipal {

    override fun getName(): String = authenticatedUser.id

    override fun toString(): String = "Siop2User(authenticatedUser=$authenticatedUser)"
}

/** Holds credentials sent from the Wallet */
@Serializable
data class AuthenticatedUser(
    val id: String,
    val timestamp: Long,
    val credentials: List<WalletCredential>,
)

/** Holds a credential sent from the Wallet */
@Serializable
data class WalletCredential(
    val allFields: Map<String, String> = mapOf(),
    val credentialType: String? = null,
)

/** Sent back to the wallet upon successful authentication */
@Serializable
data class OpenId4VpSuccess(
    @SerialName("redirect_uri")
    val redirectUri: String,
)

fun List<WalletCredential>.toOpenId4VpPrincipal() = OpenId4VpPrincipal(
    authenticatedUser = AuthenticatedUser(
        id = Json.encodeToString(this).sha256(),
        timestamp = Instant.now().toEpochMilli(),
        credentials = this
    )
)

fun WalletCredential.toOpenId4VpPrincipal() = OpenId4VpPrincipal(
    authenticatedUser = AuthenticatedUser(
        id = Json.encodeToString(this).sha256(),
        timestamp = Instant.now().toEpochMilli(),
        credentials = listOf(this)
    )
)

fun VerifiablePresentationValidationResults.toUserCredential(): List<WalletCredential> =
    validationResults.flatMap { it.toUserCredential() }

private fun AuthnResponseResult.toUserCredential(): List<WalletCredential> = when (this) {
    is AuthnResponseResult.Error -> listOf()
    is IdToken -> listOf()
    is Success -> listOf() // Plain JWT credentials not supported
    is SuccessIso -> this.toUserCredential()
    is SuccessSdJwt -> listOf(this.toUserCredential())
    is ValidationError -> listOf()
    is VerifiablePresentationValidationResults -> listOf()
    is VerifiableDCQLPresentationValidationResults -> this.toUserCredential()
}

fun SuccessSdJwt.toUserCredential() = WalletCredential(
    allFields = disclosures
        .filter { it.claimName != null && it.claimValue is JsonPrimitive }
        .associate { it.claimName!! to it.claimValue.jsonPrimitive.content },
    credentialType = verifiableCredentialSdJwt.verifiableCredentialType,
)

fun SuccessIso.toUserCredential() = documents.map { document ->
    WalletCredential(
        allFields = document.validItems.associate { it.elementIdentifier to it.elementValueToString() },
        credentialType = document.mso.docType,
    )
}

fun VerifiableDCQLPresentationValidationResults.toUserCredential() = this.validationResults.values.flatMap {
    it.toUserCredential()
}

private fun String.sha256() = runCatching {
    MessageDigest.getInstance("SHA-256").digest(this.encodeToByteArray()).encodeToString(Base64UrlStrict)
}.getOrElse { this.hashCode().toString() }

private fun IssuerSignedItem.elementValueToString() = when (elementValue) {
    is ByteArray -> (elementValue as ByteArray).encodeToString(Base64())
    is Array<*> -> (elementValue as Array<*>).contentToString()
    else -> elementValue.toString()
}

