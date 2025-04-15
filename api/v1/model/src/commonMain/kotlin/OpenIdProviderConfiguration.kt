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

package org.eclipse.apoapsis.ortserver.api.v1.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class OpenIdProviderConfiguration(
    @SerialName("acr_values_supported")
    val acrValuesSupported: List<String>? = null,

    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,

    @SerialName("authorization_encryption_alg_values_supported")
    val authorizationEncryptionAlgValuesSupported: List<String>? = null,

    @SerialName("authorization_encryption_enc_values_supported")
    val authorizationEncryptionEncValuesSupported: List<String>? = null,

    @SerialName("authorization_response_iss_parameter_supported")
    val authorizationResponseIssParameterSupported: Boolean? = null,

    @SerialName("authorization_signing_alg_values_supported")
    val authorizationSigningAlgValuesSupported: List<String>? = null,

    @SerialName("backchannel_authentication_endpoint")
    val backchannelAuthenticationEndpoint: String? = null,

    @SerialName("backchannel_authentication_request_signing_alg_values_supported")
    val backchannelAuthenticationRequestSigningAlgValuesSupported: List<String>? = null,

    @SerialName("backchannel_token_delivery_modes_supported")
    val backchannelTokenDeliveryModesSupported: List<String>? = null,

    @SerialName("backchannel_logout_supported")
    val backchannelLogoutSupported: Boolean? = null,

    @SerialName("backchannel_logout_session_supported")
    val backchannelLogoutSessionSupported: Boolean? = null,

    @SerialName("check_session_iframe")
    val checkSessionIframe: String? = null,

    @SerialName("claims_supported")
    val claimsSupported: List<String>? = null,

    @SerialName("claims_parameter_supported")
    val claimsParameterSupported: Boolean? = null,

    @SerialName("claim_types_supported")
    val claimTypesSupported: List<String>? = null,

    @SerialName("claims_locales_supported")
    val claimsLocalesSupported: List<String>? = null,

    @SerialName("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String>? = null,

    @SerialName("device_authorization_endpoint")
    val deviceAuthorizationEndpoint: String? = null,

    @SerialName("display_values_supported")
    val displayValuesSupported: List<String>? = null,

    @SerialName("end_session_endpoint")
    val endSessionEndpoint: String? = null,

    @SerialName("frontchannel_logout_supported")
    val frontchannelLogoutSupported: Boolean? = null,

    @SerialName("frontchannel_logout_session_supported")
    val frontchannelLogoutSessionSupported: Boolean? = null,

    @SerialName("grant_types_supported")
    val grantTypesSupported: List<String>? = null,

    @SerialName("id_token_encryption_alg_values_supported")
    val idTokenEncryptionAlgValuesSupported: List<String>? = null,

    @SerialName("id_token_encryption_enc_values_supported")
    val idTokenEncryptionEncValuesSupported: List<String>? = null,

    @SerialName("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>,

    @SerialName("introspection_endpoint")
    val introspectionEndpoint: String? = null,

    @SerialName("introspection_endpoint_auth_methods_supported")
    val introspectionEndpointAuthMethodsSupported: List<String>? = null,

    @SerialName("introspection_endpoint_auth_signing_alg_values_supported")
    val introspectionEndpointAuthSigningAlgValuesSupported: List<String>? = null,

    val issuer: String,

    @SerialName("jwks_uri")
    val jwksUri: String,

    @SerialName("mtls_endpoint_aliases")
    val mtlsEndpointAliases: Map<String, String>? = null,

    @SerialName("op_policy_uri")
    val opPolicyUri: String? = null,

    @SerialName("op_tos_uri")
    val opTosUri: String? = null,

    @SerialName("pushed_authorization_request_endpoint")
    val pushedAuthorizationRequestEndpoint: String? = null,

    @SerialName("registration_endpoint")
    val registrationEndpoint: String? = null,

    @SerialName("require_pushed_authorization_requests")
    val requirePushedAuthorizationRequests: Boolean? = null,

    @SerialName("require_request_uri_registration")
    val requireRequestUriRegistration: Boolean? = null,

    @SerialName("request_object_encryption_alg_values_supported")
    val requestObjectEncryptionAlgValuesSupported: List<String>? = null,

    @SerialName("request_object_encryption_enc_values_supported")
    val requestObjectEncryptionEncValuesSupported: List<String>? = null,

    @SerialName("request_object_signing_alg_values_supported")
    val requestObjectSigningAlgValuesSupported: List<String>? = null,

    @SerialName("request_parameter_supported")
    val requestParameterSupported: Boolean? = null,

    @SerialName("request_uri_parameter_supported")
    val requestUriParameterSupported: Boolean? = null,

    @SerialName("response_modes_supported")
    val responseModesSupported: List<String>? = null,

    @SerialName("response_types_supported")
    val responseTypesSupported: List<String>,

    @SerialName("revocation_endpoint")
    val revocationEndpoint: String? = null,

    @SerialName("revocation_endpoint_auth_methods_supported")
    val revocationEndpointAuthMethodsSupported: List<String>? = null,

    @SerialName("revocation_endpoint_auth_signing_alg_values_supported")
    val revocationEndpointAuthSigningAlgValuesSupported: List<String>? = null,

    @SerialName("scopes_supported")
    val scopesSupported: List<String>? = null,

    @SerialName("service_documentation")
    val serviceDocumentation: String? = null,

    @SerialName("subject_types_supported")
    val subjectTypesSupported: List<String>,

    @SerialName("tls_client_certificate_bound_access_tokens")
    val tlsClientCertificateBoundAccessTokens: Boolean? = null,

    @SerialName("token_endpoint")
    val tokenEndpoint: String,

    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>? = null,

    @SerialName("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgValuesSupported: List<String>? = null,

    @SerialName("ui_locales_supported")
    val uiLocalesSupported: List<String>? = null,

    @SerialName("userinfo_encryption_alg_values_supported")
    val userinfoEncryptionAlgValuesSupported: List<String>? = null,

    @SerialName("userinfo_encryption_enc_values_supported")
    val userinfoEncryptionEncValuesSupported: List<String>? = null,

    @SerialName("userinfo_endpoint")
    val userinfoEndpoint: String? = null,

    @SerialName("userinfo_signing_alg_values_supported")
    val userinfoSigningAlgValuesSupported: List<String>? = null
)
