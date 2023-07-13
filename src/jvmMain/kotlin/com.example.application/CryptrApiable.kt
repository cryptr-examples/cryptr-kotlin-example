package com.example.application

import cryptr.kotlin.*
import cryptr.kotlin.enums.ApplicationType
import cryptr.kotlin.models.*
import cryptr.kotlin.models.Application
import cryptr.kotlin.models.List
import cryptr.kotlin.models.jwt.JWTPayload
import cryptr.kotlin.models.jwt.JWTToken
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class CryptrApiable(
    val cryptr: Cryptr,
    val logLevel: String,
    val initWithToken: Boolean = true
) {
    init {
        println("cryptr set log level $logLevel")
        cryptr.setLogLevel(logLevel)
        if (initWithToken) try {
            cryptr.retrieveApiKeyToken()
        } catch (e: Exception) {
            println("error while init api key token")
            println(e.message)
        }
    }

    suspend fun handleHeadlessRequest(call: ApplicationCall) {
        val params = call.parameters
        val orgDomain = params.get("org_domain")
        val userEmail = params.get("user_email")
        val createSSOSamlChallengeResponse = cryptr.createSsoSamlChallenge(orgDomain = orgDomain, userEmail = userEmail)
        if (createSSOSamlChallengeResponse is APISuccess) {
            val authUrl = createSSOSamlChallengeResponse.value.authorizationUrl
            call.respondRedirect(authUrl)
        } else {
            call.respondText(cryptr.toJSONString(createSSOSamlChallengeResponse), ContentType.Application.Json)
        }
    }

    suspend fun handleHeadlessCallback(call: ApplicationCall) {
        val callbackResp = cryptr.validateSsoChallenge(call.parameters.get("code"))
        if (callbackResp is APISuccess) {
            val challengeResponse = callbackResp.value
            val idClaims = challengeResponse.getIdClaims(cryptr.cryptrServiceUrl)
            if (idClaims is JWTToken) {
                call.respondText(
                    cryptr.format.encodeToString<JWTPayload>(idClaims.payload),
                    ContentType.Application.Json
                )
            } else {
                call.respondText(cryptr.toJSONString(challengeResponse), ContentType.Application.Json)
            }
        } else if (callbackResp is APIError) {
            call.respondText(cryptr.toJSONString(callbackResp.error), ContentType.Application.Json)
        } else {
            call.respondText(callbackResp.toString(), ContentType.Application.Json)
        }
    }

    suspend fun createOrganization(call: ApplicationCall) {
        try {
            val organizationName = call.parameters.getOrFail("name")
            val allowedEmailDomains = call.parameters.getAll("allowed_email_domains[]")
            val organizationResponse = cryptr.createOrganization(organizationName, allowedEmailDomains?.toSet())
            if (organizationResponse is APISuccess) {
                val organization = organizationResponse.value
                val orgPayload = cryptr.toJSONString(organization)
                val payload = JSONObject()
                    .put("organization", JSONObject(orgPayload))
                    .put(
                        "create_sso_connection",
                        "http://localhost:8080/create-sso-connection?org_domain=${organization.domain}"
                    )
                call.respondText(payload.toString(2), ContentType.Application.Json)
            } else {
                call.respondText(cryptr.toJSONString(organizationResponse), ContentType.Application.Json)

            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }

    suspend fun listOrganizations(call: ApplicationCall) {
        if (call.parameters.contains("org_domain")) {
            val org = cryptr.retrieveOrganization(domain = call.parameters.getOrFail("org_domain"))
            call.respondText(cryptr.toJSONString(org), ContentType.Application.Json)
        } else {
            val perPage = call.parameters.get("per_page")?.toInt()
            val currentPage = call.parameters.get("current_page")?.toInt()
            val listing = cryptr.listOrganizations(perPage, currentPage)
            if (call.parameters.contains("raw")) {
                if (listing is APISuccess) {
                    call.respondText(
                        cryptr.toJSONListString(listing as APIResult<List<CryptrResource>, ErrorMessage>),
                        ContentType.Application.Json
                    )
                } else {
                    call.respondText(
                        listing.toString(),
                        ContentType.Application.Json
                    )
                }
            } else {
                call.respondText(
                    cryptr.toJSONString(listing),
                    ContentType.Application.Json
                )
            }
        }
    }

    suspend fun deleteOrganization(call: ApplicationCall) {
        val domain = call.parameters.getOrFail("org_domain")
        val org = cryptr.retrieveOrganization(domain)
        if (org is APISuccess) {
            call.respondText(
                cryptr.toJSONString(cryptr.deleteOrganization(org.value)!!),
                ContentType.Application.Json
            )
        } else {
            call.respondText(cryptr.toJSONString(org), ContentType.Application.Json)
        }
    }

    suspend fun listUsers(call: ApplicationCall) {
        val organizationDomain = call.parameters.getOrFail("org_domain")
        if (call.parameters.contains("id")) {
            val user = cryptr.retrieveUser(organizationDomain, userId = call.parameters.getOrFail("id"))
            call.respondText(cryptr.toJSONString(user), ContentType.Application.Json)
        } else {
            val perPage = call.parameters.get("per_page")?.toIntOrNull()
            val currentPage = call.parameters.get("current_page")?.toIntOrNull()
            val listing = cryptr.listUsers(organizationDomain, perPage, currentPage)
            if (call.parameters.contains("raw")) {
                if (listing is APISuccess) {
                    call.respondText(
                        cryptr.toJSONListString(listing as APIResult<List<CryptrResource>, ErrorMessage>),
                        ContentType.Application.Json
                    )
                } else {
                    call.respondText(
                        listing.toString(),
                        ContentType.Application.Json
                    )
                }
            } else {
                call.respondText(
                    cryptr.toJSONString(listing),
                    ContentType.Application.Json
                )
            }
        }
    }

    suspend fun createUser(call: ApplicationCall) {
        val organizationDomain = call.parameters.getOrFail("org_domain")
        val userEmail = getRandomString(12) + "@$organizationDomain.io"
        val resp = cryptr.createUser(
            organizationDomain,
            User(email = userEmail, profile = Profile(givenName = "Toto", familyName = getRandomString(9)))
        )
        call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
    }

    suspend fun updateUser(call: ApplicationCall) {
        val organizationDomain = call.parameters.getOrFail("org_domain")
        val userId = call.parameters.getOrFail("id")
        userId.plus("toto")
        val response = cryptr.retrieveUser(organizationDomain, userId)
        if (response is APISuccess) {
            println("response.value")
            println(response.value)
            call.respondText(
                cryptr.toJSONString(cryptr.updateUser(updateUser(response.value))),
                ContentType.Application.Json
            )
        } else {
            call.respondText(cryptr.toJSONString(response), ContentType.Application.Json)
        }
    }

    suspend fun deleteUser(call: ApplicationCall) {
        val domain = call.parameters.getOrFail("org_domain")
        val userId = call.parameters.getOrFail("id")
        val result = cryptr.retrieveUser(domain, userId)
        if (result is APISuccess) {
            call.respondText(
                cryptr.deleteUser(result.value).toString(),
                ContentType.Application.Json
            )
        } else {
            call.respondText(cryptr.toJSONString(result), ContentType.Application.Json)
        }
    }

    suspend fun listApplications(call: ApplicationCall) {
        val organizationDomain = call.parameters.getOrFail("org_domain")
        if (call.parameters.contains("id")) {
            val application = cryptr.retrieveApplication(organizationDomain, call.parameters.getOrFail("id"))
            call.respondText(cryptr.toJSONString(application), ContentType.Application.Json)
        } else {
            val perPage = call.parameters.get("per_page")?.toInt()
            val currentPage = call.parameters.get("current_page")?.toInt()
            val listing = cryptr.listApplications(organizationDomain, perPage, currentPage)
            if (call.parameters.contains("raw")) {
                if (listing is APISuccess) {
                    call.respondText(
                        cryptr.toJSONListString(listing as APIResult<List<CryptrResource>, ErrorMessage>),
                        ContentType.Application.Json
                    )
                } else {
                    call.respondText(
                        listing.toString(),
                        ContentType.Application.Json
                    )
                }
            } else {
                call.respondText(
                    cryptr.toJSONString(listing),
                    ContentType.Application.Json
                )
            }
        }
    }

    suspend fun createApplications(call: ApplicationCall) {
        val organizationDomain = call.parameters.getOrFail("org_domain")
        val name = "Application " + organizationDomain + " " + getRandomString(12)
        val urls = setOf("http://localhost:4242")
        val resp = cryptr.createApplication(
            organizationDomain,
            Application(
                name = name,
                applicationType = ApplicationType.REGULAR_WEB,
                allowedLogoutUrls = urls,
                allowedRedirectUrls = urls,
                allowedOriginsCors = urls
            )
        )
        call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
    }

    suspend fun deleteteApplications(call: ApplicationCall) {
        val domain = call.parameters.getOrFail("org_domain")
        val userId = call.parameters.getOrFail("id")
        val result = cryptr.retrieveApplication(domain, userId)
        if (result is APISuccess) {
            call.respondText(
                cryptr.deleteApplication(result.value).toString(),
                ContentType.Application.Json
            )
        } else {
            call.respondText(cryptr.toJSONString(result), ContentType.Application.Json)
        }
    }

    suspend fun inviteAdminOnboarding(call: ApplicationCall) {
        try {
            val orgDomain = call.parameters.getOrFail("org_domain")
            val providerType = call.parameters.get("provider_type")
            val itAdminEmail = call.parameters.get("it_admin_email")
            val emailTemplateId = call.parameters.get("email_template_id")
            val sendEmail: Boolean = call.parameters.get("send_email") == "true"
            val resp = cryptr.inviteSsoAdminOnboarding(
                orgDomain = orgDomain,
                itAdminEmail = itAdminEmail
            )
            if (resp is APISuccess) {
                call.respondText(
                    cryptr.toJSONString(resp),
                    ContentType.Application.Json
                )
            } else {
                call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }

    }

    suspend fun retrieveAdminOnboarding(call: ApplicationCall) {
        try {
            val organizationDomain = call.parameters.getOrFail("org_domain")
            val resp = cryptr.retrieveAdminOnboarding(organizationDomain, "sso-connection")
            call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }


    }

    suspend fun resetAdminOnboarding(call: ApplicationCall) {
        try {
            val organizationDomain = call.parameters.getOrFail("org_domain")
            val resp = cryptr.resetAdminOnboarding(organizationDomain, "sso-connection")
            call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }


    }

    suspend fun createSSOConnection(call: ApplicationCall) {
        try {
            val orgDomain = call.parameters.getOrFail("org_domain")
            val providerType = call.parameters.get("provider_type")
            val applicationId = call.parameters.get("application_id")
            val ssoAdminEmail = call.parameters.get("email")
            val sendEmail = call.parameters.get("send_email") == "true"
            val resp = cryptr.createSsoConnection(orgDomain, providerType, applicationId, ssoAdminEmail, sendEmail)
            if (resp is APISuccess) {
                val ssoConnection = resp.value
                val connectionPayload = JSONObject(cryptr.toJSONString(ssoConnection))
                val payload = JSONObject()
                    .put("sso_connection", connectionPayload)
                    .put(
                        "create_sso_admin_onboarding",
                        "http://localhost:8080/create-sso-admin-onboarding?org_domain=${orgDomain}&email_template_id=${"fd783cb9-21d5-4cbf-a86b-f36cd7581deb"}"
                    )
                call.respondText(payload.toString(2), ContentType.Application.Json)
            } else {
                call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }

    suspend fun listSsoConnections(call: ApplicationCall) {
        try {
            val perPage = call.parameters.get("per_page")?.toInt()
            val currentPage = call.parameters.get("current_page")?.toInt()
            val listing = cryptr.listSsoConnections(perPage, currentPage)
            if (call.parameters.contains("raw")) {
                if (listing is APISuccess) {
                    call.respondText(
                        cryptr.toJSONListString(listing as APIResult<List<CryptrResource>, ErrorMessage>),
                        ContentType.Application.Json
                    )
                } else {
                    call.respondText(
                        listing.toString(),
                        ContentType.Application.Json
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }

    suspend fun retrieveSsoConnection(call: ApplicationCall) {
        try {
            val orgDomain = call.parameters.getOrFail("org_domain")
            val response = cryptr.retrieveSsoConnection(orgDomain)
            call.respondText(cryptr.toJSONString(response), ContentType.Application.Json)
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }

    suspend fun createSsoAdminOnboarding(call: ApplicationCall) {
        try {
            val orgDomain = call.parameters.getOrFail("org_domain")
            val itAdminEmail = call.parameters.getOrFail("email")
            val response = cryptr.createSsoAdminOnboarding(
                orgDomain = orgDomain,
                itAdminEmail = itAdminEmail
            )
            if (response is APISuccess) {
                val onobardingPayload = JSONObject(cryptr.toJSONString(response.value))
                val payload = JSONObject()
                    .put("admin_onboarding", onobardingPayload)
                    .put(
                        "invite-admin-onboarding",
                        "http://localhost:8080/invite-sso-admin-onboarding?org_domain=${orgDomain}&it_admin_email=thibaud@crpytr.co"
                    )
                call.respondText(payload.toString(), ContentType.Application.Json)
            } else {
                call.respondText(cryptr.toJSONString(response), ContentType.Application.Json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }

    suspend fun authenticateUsingPassword(call: ApplicationCall) {
        try {
            val orgDomain = call.parameters.getOrFail("org_domain")
            val userEmail = call.parameters.getOrFail("user_email")
            val plainText = call.parameters.get("plain_text")
            val passwordChallengeResponse = cryptr.createPasswordChallenge(orgDomain, userEmail, plainText)
            if (passwordChallengeResponse is APISuccess) {
                val passwordChallenge = passwordChallengeResponse.value
                println("passwordChallenge")
                println(cryptr.toJSONString(passwordChallenge))

                if (passwordChallenge.isExpired()) {
                    cryptr.createPassword(
                        userEmail = userEmail,
                        plaintText = plainText!!,
                        passwordCode = passwordChallenge.getRenewCode(),
                        orgDomain = orgDomain
                    )
                } else if (passwordChallenge.isSuccess()) {
                    val code = passwordChallenge.code
                    val passwordTokenResponse = cryptr.getPasswordChallengeTokens(code)
                    println(passwordTokenResponse)
                    //OR cryptr.getPasswordChallengeTokens(passwordChallenge)
                    if (passwordTokenResponse is APISuccess) {
                        val passwordChallengeResponse: PasswordChallengeResponse = passwordTokenResponse.value
                        call.respondText(
                            cryptr.toJSONString(passwordChallengeResponse),
                            ContentType.Application.Json
                        )
                    } else {
                        call.respondText(cryptr.toJSONString(passwordTokenResponse), ContentType.Application.Json)
                    }
                } else {
                    call.respondText(cryptr.toJSONString(passwordChallenge), ContentType.Application.Json)

                }
            } else {
                call.respondText(cryptr.toJSONString(passwordChallengeResponse), ContentType.Application.Json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }

    suspend fun createPasswordRequest(call: ApplicationCall) {
        try {
            val orgDomain = call.parameters.getOrFail("org_domain")
            val userEmail = call.parameters.getOrFail("user_email")
            val formatter = DateTimeFormatter.ofPattern("dd_HH_mm")
            val current = LocalDateTime.now().format(formatter)
            val newPlainText = "test2023A$current"
            println("newPlainText $newPlainText")
            val redirectUri = "http://localhost:8080/password-callback?new_plain_text=$newPlainText"
            val createPasswordRequestResponse = cryptr.createPasswordRequest(userEmail, redirectUri, orgDomain)
            if (createPasswordRequestResponse is APISuccess) {
                val passwordRequest = createPasswordRequestResponse.value
                call.respondText(cryptr.toJSONString(passwordRequest), ContentType.Application.Json)
            } else {
                call.respondText(cryptr.toJSONString(createPasswordRequestResponse), ContentType.Application.Json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }

    suspend fun passwordCallback(call: ApplicationCall) {
        try {
            val passwordCode = call.parameters.getOrFail("password_code")
            val newPlainText = call.parameters.getOrFail("new_plain_text")

            val resp = cryptr.createPassword(passwordCode, newPlainText)
            if (resp is APISuccess) {
                val value = resp.value
                call.respondText(cryptr.toJSONString(value), ContentType.Application.Json)
            } else {
                call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }

    suspend fun requestPasswordWithoutEmail(call: ApplicationCall) {
        try {
            val userEmail = call.parameters.getOrFail("user_email")
            val plainText = call.parameters.getOrFail("plain_text")
            val orgDomain = call.parameters.getOrFail("org_domain")

            val resp = cryptr.createPasswordWithoutEmailVerification(userEmail, plainText, orgDomain)
            if (resp is APISuccess) {
                val value = resp.value
                val pwdCode = value.passwordCode
                println("pwdCode: $pwdCode")
                if (pwdCode !== null) {
                    val tokenResponse = cryptr.getPasswordChallengeTokens(pwdCode)
                    if (tokenResponse is APISuccess) {
                        call.respondText(cryptr.toJSONString(tokenResponse.value), ContentType.Application.Json)
                    } else {
                        call.respondText(cryptr.toJSONString(tokenResponse), ContentType.Application.Json)
                    }
                } else {
                    call.respondText(cryptr.toJSONString(value), ContentType.Application.Json)
                }

            } else {
                call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }

    }
}