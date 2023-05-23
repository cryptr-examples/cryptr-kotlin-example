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
        val createSSOSamlChallengeResponse = cryptr.createSSOSamlChallenge(orgDomain = orgDomain, userEmail = userEmail)
        if (createSSOSamlChallengeResponse is APISuccess) {
            val authUrl = createSSOSamlChallengeResponse.value.authorizationUrl
            call.respondRedirect(authUrl)
        } else {
            call.respondText(cryptr.toJSONString(createSSOSamlChallengeResponse), ContentType.Application.Json)
        }
    }

    suspend fun handleHeadlessCallback(call: ApplicationCall) {
        val callbackResp = cryptr.validateSSOChallenge(call.parameters.get("code"))
        if (callbackResp is APISuccess) {
            val challengeResponse = callbackResp.value
            println(cryptr.toJSONString(challengeResponse))
            val idClaims = challengeResponse.getIdClaims(cryptr.cryptrBaseUrl)
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
            call.respondText(cryptr.toJSONString(organizationResponse), ContentType.Application.Json)
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }

    suspend fun listOrganizations(call: ApplicationCall) {
        if (call.parameters.contains("org_domain")) {
            val org = cryptr.getOrganization(domain = call.parameters.getOrFail("org_domain"))
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
        val org = cryptr.getOrganization(domain)
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
            val user = cryptr.getUser(organizationDomain, userId = call.parameters.getOrFail("id"))
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
        val response = cryptr.getUser(organizationDomain, userId)
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
        val result = cryptr.getUser(domain, userId)
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
            val application = cryptr.getApplication(organizationDomain, call.parameters.getOrFail("id"))
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
        val result = cryptr.getApplication(domain, userId)
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
            val ssoAdminEmail = call.parameters.get("email")
            val emailTemplateId = call.parameters.get("email_template_id")
            val sendEmail: Boolean = call.parameters.get("send_email") == "true"
            val resp = cryptr.inviteSSOAdminOnboarding(
                organizationDomain = orgDomain
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
            val resp = cryptr.getAdminOnboarding(organizationDomain, "sso-connection")
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
            val resp = cryptr.createSSOConnection(orgDomain, providerType, applicationId, ssoAdminEmail, sendEmail)
            if (resp is APISuccess) {
                call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
            } else {
                call.respondText(cryptr.toJSONString(resp), ContentType.Application.Json)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            call.respondText("{\"error\": \"${e.message}\"}", ContentType.Application.Json)
        }
    }
}