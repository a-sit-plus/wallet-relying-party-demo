package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.openid.RequestOptionsCredential
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Sent from `index.html`, used in [ApiController.postTransactionCreate] */
@Serializable
data class TransactionRequest(
    /** URL prefix for the embedded URL in the QR Code, e.g. `haip://` */
    val urlprefix: String,
    /** Credentials to request from wallet */
    val credentials: List<TransactionRequestCredential>,
) {

    fun toRequestOptionsCredentials() = credentials.map { it.toRequestOptionsCredential() }.toSet()
}

/** Sent from `index.html`, used in [ApiController.postTransactionCreate] */
@Serializable
data class TransactionRequestCredential(
    /** Set an SD-JWT type or ISO doctype of a credential known to this service */
    val credentialType: String? = null,
    /** Set `SD_JWT` or `PLAIN_JWT` or `ISO_MDOC` */
    val representation: String? = null,
    /** Optionally set which attributes should be requested from the Wallet */
    val attributes: List<String>? = null,
) {
    fun toRequestOptionsCredential() = RequestOptionsCredential(
        credentialScheme = credentialType
            ?.let { AttributeIndex.resolveCredential(it)?.first }
            ?: EuPidScheme,
        representation = CredentialRepresentation.entries.firstOrNull { it.name == representation }
            ?: CredentialRepresentation.SD_JWT,
        // TODO or use requestedAttributes to enforce those attributes
        requestedOptionalAttributes = attributes?.ifEmpty { null }?.toSet(),
    )
}

/** Sent to `index.html` to display a QR Code that can be scanned by wallet apps, from [ApiController.postTransactionCreate] */
@Serializable
data class TransactionResponse(
    @Serializable(ByteArrayToBase64Serializer::class)
    val qrCodePng: ByteArray,
    val qrCodeUrl: String,
    val id: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionResponse

        if (!qrCodePng.contentEquals(other.qrCodePng)) return false
        if (qrCodeUrl != other.qrCodeUrl) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = qrCodePng.contentHashCode()
        result = 31 * result + qrCodeUrl.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}

object ByteArrayToBase64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ByteArrayToBase64", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ByteArray = decoder.decodeString().decodeToByteArray(Base64())

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(value.encodeToString(Base64()))
    }

}

/** Represents a transaction, i.e. an open user authentication flow */
@Serializable
data class Transaction(
    val id: String,
    val request: TransactionRequest
)