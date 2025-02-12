package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.RelyingPartyMetadata
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.openid.RequestOptions
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.springframework.web.util.UriComponentsBuilder
import qrcode.QRCode
import java.util.*
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@Controller
class ApiController(
    @Value("\${app.public-url}")
    private val publicUrl: String,
    private val userStore: UserStore,
) {
    /** Stores active authentication transactions */
    private val transactions: MutableMap<String, Transaction> = HashMap()

    private val clientId = publicUrl.getDnsName()

    /** Key material used to sign authentication requests */
    private val verifierKeyMaterial = EphemeralKeyWithoutCert()

    /** Verifier agent from vc-k */
    private val verifierAgent: VerifierAgent = VerifierAgent(
        identifier = clientId,
    )

    /** Implements OpenId4VP, from vc-k, can be customized with more constructor parameters */
    private val openId4VpVerifier: OpenId4VpVerifier by lazy {
        OpenId4VpVerifier(
            verifier = verifierAgent,
            keyMaterial = verifierKeyMaterial,
            /** Could be any other subclass of [at.asitplus.wallet.lib.openid.ClientIdScheme] */
            clientIdScheme = ClientIdScheme.RedirectUri(clientId)
        )
    }

    /** Used to redirect the wallet after authentication, see [postTransactionResult] */
    private val authenticationSuccessUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("success.html")
            .toUriString()
    }

    /** See [getMetadata] */
    private val metadataUrl by lazy {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("openid4vp", "metadata")
            .toUriString()
    }

    @GetMapping("/api/items")
    @ResponseBody
    fun apiItems(): List<OpenId4VpPrincipal> = userStore.listAllUsers()

    /** Called by `success.html` */
    @GetMapping("/api/single/{id}")
    @ResponseBody
    fun apiSingle(@PathVariable id: String): ResponseEntity<OpenId4VpPrincipal> =
        userStore.loadUser(id)?.let {
            Napier.i("/api/single/$id returns $it")
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(it)
        } ?: ResponseEntity.notFound().build()

    /** Can be used to remove an authenticated user from the user store */
    @PostMapping("/api/remove")
    @ResponseBody
    fun removeApiItem(@RequestBody id: String): ResponseEntity<OpenId4VpPrincipal> =
        userStore.removeUser(id).let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    /** Called by `index.html` to create a new transaction, i.e. display QR code for wallet apps to scan. */
    @OptIn(ExperimentalUuidApi::class)
    @PostMapping("/transaction/create", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun postTransactionCreate(
        @RequestBody request: TransactionRequest,
    ): ResponseEntity<TransactionResponse> = runBlocking {
        Napier.i("/transaction/create called with $request")
        val transactionId = Uuid.random().toString()
        val authnUrl = buildAuthnUrlForWallet(request, transactionId)
        val qrCodeBytes = QRCode.ofSquares().build(authnUrl).render().getBytes()
        val response = TransactionResponse(qrCodeBytes, authnUrl, transactionId)
        Napier.i("/transaction/create returns $transactionId with $authnUrl")
        ResponseEntity.ok().body(response)
    }

    /** Called by the wallet app to load the authentication request object. See [buildTransactionUrl] */
    @GetMapping("/transaction/get/{id}")
    @ResponseBody
    fun getTransaction(
        @PathVariable id: String,
    ): ResponseEntity<String> = runBlocking {
        Napier.i("/transaction/$id called")
        val transactionRequest = transactions[id]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("/transaction/$id returns NOT_FOUND") }
        val state = Base64.getEncoder().encodeToString(Random.nextBytes(32))
        val requestOptions = RequestOptions(
            state = state,
            responseMode = OpenIdConstants.ResponseMode.DirectPost,
            responseUrl = buildPostSuccessUrl(id),
            credentials = transactionRequest.request.toRequestOptionsCredentials(),
        )
        val requestObjectJws = openId4VpVerifier.createAuthnRequestAsSignedRequestObject(requestOptions).getOrElse {
            Napier.w("/transaction/$id error", it)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage)
        }
        val result = requestObjectJws.serialize()
            .also { Napier.i("/transaction/$id returns $it") }
        ResponseEntity.ok(result)
    }

    /**
     * Expects SIOPv2 authn response as request body,
     * called from Wallet App upon answering authn request from [getTransaction].
     *
     * See [buildPostSuccessUrl] and [authenticationSuccessUrl].
     */
    @PostMapping("/transaction/result/{id}")
    fun postTransactionResult(
        @PathVariable id: String,
        @RequestBody requestBody: String,
    ): ResponseEntity<OpenId4VpSuccess> = runBlocking {
        Napier.i("/transaction/result/$id called with $requestBody")
        if (transactions.remove(id) == null) {
            Napier.w("/transaction/result/$id returns NOT_FOUND")
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        Napier.i("postTransactionResult with $requestBody")
        val user = openId4VpVerifier.validateAuthnResponse(requestBody).toOpenId4VpPrincipal()
        Napier.i("Storing user for transaction $id: $user")
        userStore.put(id, user)
        val redirectUrlWithId = ServletUriComponentsBuilder
            .fromHttpUrl(authenticationSuccessUrl)
            .queryParam("id", id)
            .toUriString()
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(OpenId4VpSuccess(redirectUrlWithId))
    }

    /** URL for Wallet, transported as QR Code for scanning */
    private fun buildAuthnUrlForWallet(request: TransactionRequest, transactionId: String) =
        ServletUriComponentsBuilder.fromUriString(request.urlprefix)
            .queryParam("request_uri", buildTransactionUrl(request, transactionId))
            .queryParam("client_id", publicUrl.getDnsName())
            .queryParam("client_metadata_uri", metadataUrl)
            .toUriString()

    /** Included in URL for wallet in [buildAuthnUrlForWallet], and then called in [getTransaction] */
    private fun buildTransactionUrl(request: TransactionRequest, transactionId: String) = runBlocking {
        transactions[transactionId] = Transaction(transactionId, request)
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("transaction", "get", transactionId)
            .toUriString()
    }

    /** See [postTransactionResult] */
    private fun buildPostSuccessUrl(transactionId: String) = runBlocking {
        ServletUriComponentsBuilder.fromHttpUrl(publicUrl)
            .pathSegment("transaction", "result", transactionId)
            .toUriString()
    }

    /** Wallets can resolve metadata of this service passed by-reference in authn requests, see [metadataUrl] */
    @ResponseBody
    @GetMapping("/openid4vp/metadata")
    fun getMetadata(): ResponseEntity<RelyingPartyMetadata> = runBlocking {
        Napier.i("/openid4vp/metadata called")
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(openId4VpVerifier.metadata)
    }

    /** Extracts data from VC-K, converts to [OpenId4VpPrincipal] */
    private suspend fun AuthnResponseResult.toOpenId4VpPrincipal(): OpenId4VpPrincipal =
        when (this) {
            is AuthnResponseResult.Success ->
                throw RuntimeException("Plain JWT not supported")

            is AuthnResponseResult.SuccessSdJwt ->
                this.toUserCredential().toOpenId4VpPrincipal()

            is AuthnResponseResult.SuccessIso ->
                this.toUserCredential().toOpenId4VpPrincipal()

            is AuthnResponseResult.Error ->
                throw RuntimeException(this.reason)

            is AuthnResponseResult.ValidationError ->
                throw RuntimeException("Validation failed for field: ${this.field}")

            is AuthnResponseResult.VerifiablePresentationValidationResults ->
                this.toUserCredential().toOpenId4VpPrincipal()

            is AuthnResponseResult.IdToken ->
                throw RuntimeException("Only got id_token")
        }

}

private fun String.getDnsName() = UriComponentsBuilder.fromUriString(this).build().host ?: "wallet.a-sit.at"

