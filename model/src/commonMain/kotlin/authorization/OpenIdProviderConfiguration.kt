/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.eclipse.apoapsis.ortserver.model.authorization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * Data class defining the configuration of an OpenId Provider.
 * See https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class OpenIdProviderConfiguration(
    /**
     * List of supported Authentication Context Class References (ACR).
     */
    @SerialName("acr_values_supported")
    val acrValuesSupported: List<String>? = null,

    /**
     * The authorization endpoint URI.
     */
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,

    /**
     * List of supported authorization encryption algorithms.
     */
    @SerialName("authorization_encryption_alg_values_supported")
    val authorizationEncryptionAlgValuesSupported: List<String>? = null,

    /**
     * List of supported authorization encryption encoding methods.
     */
    @SerialName("authorization_encryption_enc_values_supported")
    val authorizationEncryptionEncValuesSupported: List<String>? = null,

    /**
     * Indicates whether authorization response issuer parameter is supported.
     */
    @SerialName("authorization_response_iss_parameter_supported")
    val authorizationResponseIssParameterSupported: Boolean? = null,

    /**
     * List of supported authorization signing algorithms.
     */
    @SerialName("authorization_signing_alg_values_supported")
    val authorizationSigningAlgValuesSupported: List<String>? = null,

    /**
     * The backchannel authentication endpoint URI.
     */
    @SerialName("backchannel_authentication_endpoint")
    val backchannelAuthenticationEndpoint: String? = null,

    /**
     * List of supported backchannel authentication request signing algorithms.
     */
    @SerialName("backchannel_authentication_request_signing_alg_values_supported")
    val backchannelAuthenticationRequestSigningAlgValuesSupported: List<String>? = null,

    /**
     * List of supported backchannel token delivery modes.
     */
    @SerialName("backchannel_token_delivery_modes_supported")
    val backchannelTokenDeliveryModesSupported: List<String>? = null,

    /**
     * Indicates whether backchannel logout is supported.
     */
    @SerialName("backchannel_logout_supported")
    val backchannelLogoutSupported: Boolean? = null,

    /**
     * Indicates whether backchannel logout session is supported.
     */
    @SerialName("backchannel_logout_session_supported")
    val backchannelLogoutSessionSupported: Boolean? = null,

    /**
     * The check session iframe URI.
     */
    @SerialName("check_session_iframe")
    val checkSessionIframe: String? = null,

    /**
     * List of claims supported.
     */
    @SerialName("claims_supported")
    val claimsSupported: List<String>? = null,

    /**
     * Indicates whether claims parameter is supported.
     */
    @SerialName("claims_parameter_supported")
    val claimsParameterSupported: Boolean? = null,

    /**
     * List of OAuth 2.0 claim types supported.
     */
    @SerialName("claim_types_supported")
    val claimTypesSupported: List<String>? = null,

    /**
     * List of supported claims locales.
     */
    @SerialName("claims_locales_supported")
    val claimsLocalesSupported: List<String>? = null,

    /**
     * List of supported code challenge methods.
     */
    @SerialName("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String>? = null,

    /**
     * The device authorization endpoint URI.
     */
    @SerialName("device_authorization_endpoint")
    val deviceAuthorizationEndpoint: String? = null,

    /**
     * List of supported display values.
     */
    @SerialName("display_values_supported")
    val displayValuesSupported: List<String>? = null,

    /**
     * The end-session endpoint URI.
     */
    @SerialName("end_session_endpoint")
    val endSessionEndpoint: String? = null,

    /**
     * Flag indicating whether frontchannel logout is supported.
     */
    @SerialName("frontchannel_logout_supported")
    val frontchannelLogoutSupported: Boolean? = null,

    /**
     * Flag indicating whether frontchannel logout sessions are supported.
     */
    @SerialName("frontchannel_logout_session_supported")
    val frontchannelLogoutSessionSupported: Boolean? = null,

    /**
     * List of OAuth 2.0 grant_type values that the OpenID Provider supports.
     */
    @SerialName("grant_types_supported")
    val grantTypesSupported: List<String>? = null,

    /**
     * List of supported ID token encryption algorithms.
     */
    @SerialName("id_token_encryption_alg_values_supported")
    val idTokenEncryptionAlgValuesSupported: List<String>? = null,

    /**
     * List of supported ID token encryption encoding methods.
     */
    @SerialName("id_token_encryption_enc_values_supported")
    val idTokenEncryptionEncValuesSupported: List<String>? = null,

    /**
     * List of JWS signing algorithms supported for ID Token signatures.
     */
    @SerialName("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>,

    /**
     * The introspection endpoint URI.
     */
    @SerialName("introspection_endpoint")
    val introspectionEndpoint: String? = null,

    /**
     * List of supported introspection endpoint authentication methods.
     */
    @SerialName("introspection_endpoint_auth_methods_supported")
    val introspectionEndpointAuthMethodsSupported: List<String>? = null,

    /**
     * List of supported introspection endpoint authentication signing algorithms.
     */
    @SerialName("introspection_endpoint_auth_signing_alg_values_supported")
    val introspectionEndpointAuthSigningAlgValuesSupported: List<String>? = null,

    /**
     * The issuer identifier URI.
     */
    val issuer: String,

    /**
     * The URI of the JSON Web Key Set document.
     */
    @SerialName("jwks_uri")
    val jwksUri: String,

    /**
     * The MTLS endpoint aliases.
     */
    @SerialName("mtls_endpoint_aliases")
    val mtlsEndpointAliases: Map<String, String>? = null,

    /**
     * The OP policy URI.
     */
    @SerialName("op_policy_uri")
    val opPolicyUri: String? = null,

    /**
     * The OP terms of service URI.
     */
    @SerialName("op_tos_uri")
    val opTosUri: String? = null,

    /**
     * The pushed authorization request endpoint URI.
     */
    @SerialName("pushed_authorization_request_endpoint")
    val pushedAuthorizationRequestEndpoint: String? = null,

    /**
     * The registration endpoint URI.
     */
    @SerialName("registration_endpoint")
    val registrationEndpoint: String? = null,

    /**
     * Indicates whether pushed authorization requests are required.
     */
    @SerialName("require_pushed_authorization_requests")
    val requirePushedAuthorizationRequests: Boolean? = null,

    /**
     * Indicates whether request URI registration is required.
     */
    @SerialName("require_request_uri_registration")
    val requireRequestUriRegistration: Boolean? = null,

    /**
     * List of supported request object encryption algorithms.
     */
    @SerialName("request_object_encryption_alg_values_supported")
    val requestObjectEncryptionAlgValuesSupported: List<String>? = null,

    /**
     * List of supported request object encryption encoding methods.
     */
    @SerialName("request_object_encryption_enc_values_supported")
    val requestObjectEncryptionEncValuesSupported: List<String>? = null,

    /**
     * List of supported request object signing algorithms.
     */
    @SerialName("request_object_signing_alg_values_supported")
    val requestObjectSigningAlgValuesSupported: List<String>? = null,

    /**
     * Indicates whether request parameter is supported.
     */
    @SerialName("request_parameter_supported")
    val requestParameterSupported: Boolean? = null,

    /**
     * Indicates whether request URI parameter is supported.
     */
    @SerialName("request_uri_parameter_supported")
    val requestUriParameterSupported: Boolean? = null,

    /**
     * List of supported response modes.
     */
    @SerialName("response_modes_supported")
    val responseModesSupported: List<String>? = null,

    /**
     * List of OAuth 2.0 response_type values that the OpenID Provider supports.
     */
    @SerialName("response_types_supported")
    val responseTypesSupported: List<String>,

    /**
     * The revocation endpoint URI.
     */
    @SerialName("revocation_endpoint")
    val revocationEndpoint: String? = null,

    /**
     * List of supported revocation endpoint authentication methods.
     */
    @SerialName("revocation_endpoint_auth_methods_supported")
    val revocationEndpointAuthMethodsSupported: List<String>? = null,

    /**
     * List of supported revocation endpoint authentication signing algorithms.
     */
    @SerialName("revocation_endpoint_auth_signing_alg_values_supported")
    val revocationEndpointAuthSigningAlgValuesSupported: List<String>? = null,

    /**
     * List of OAuth 2.0 scope values that the OpenID Provider supports. Optional.
     */
    @SerialName("scopes_supported")
    val scopesSupported: List<String>? = null,

    /**
     * Service documentation URI.
     */
    @SerialName("service_documentation")
    val serviceDocumentation: String? = null,

    /**
     * List of OAuth 2.0 subject identifier types that the OpenID Provider supports.
     */
    @SerialName("subject_types_supported")
    val subjectTypesSupported: List<String>,

    /**
     * Indicates whether TLS client certificate bound access tokens are supported.
     */
    @SerialName("tls_client_certificate_bound_access_tokens")
    val tlsClientCertificateBoundAccessTokens: Boolean? = null,

    /**
     * The token endpoint URI.
     */
    @SerialName("token_endpoint")
    val tokenEndpoint: String,

    /**
     * List of client authentication methods supported by the token endpoint. Optional.
     */
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>? = null,

    /**
     * List of supported token endpoint authentication signing algorithms.
     */
    @SerialName("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgValuesSupported: List<String>? = null,

    /**
     * List of supported UI locales.
     */
    @SerialName("ui_locales_supported")
    val uiLocalesSupported: List<String>? = null,

    /**
     * List of supported userinfo encryption algorithms.
     */
    @SerialName("userinfo_encryption_alg_values_supported")
    val userinfoEncryptionAlgValuesSupported: List<String>? = null,

    /**
     * List of supported userinfo encryption encoding methods.
     */
    @SerialName("userinfo_encryption_enc_values_supported")
    val userinfoEncryptionEncValuesSupported: List<String>? = null,

    /**
     * The userinfo endpoint URI. Optional.
     */
    @SerialName("userinfo_endpoint")
    val userinfoEndpoint: String? = null,

    /**
     * List of supported userinfo signing algorithms.
     */
    @SerialName("userinfo_signing_alg_values_supported")
    val userinfoSigningAlgValuesSupported: List<String>? = null
)
