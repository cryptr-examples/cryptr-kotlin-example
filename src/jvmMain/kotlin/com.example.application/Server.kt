package com.example.application

import cryptr.kotlin.Cryptr
import cryptr.kotlin.enums.CryptrEnvironment
import cryptr.kotlin.models.Address
import cryptr.kotlin.models.Profile
import cryptr.kotlin.models.User
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun HTML.index() {
    head {
        title("Hello from Ktor!")
    }
    body {
        div {
            +"Hello from Ktor"
        }
        div {
            id = "root"
        }
        script(src = "/static/fullstack.js") {}
    }
}

fun getRandomString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

fun updateUser(user: User): User {
    return User(
        id = user.id,
        email = user.email,
        resourceDomain = user.resourceDomain,
        profile = Profile(gender = "male"),
        address = Address(
            locality = "Trouville",
            region = "Normandie",
            streetAddress = "12 rue de la plage",
            postalCode = "76890",
            country = "FR",
        ),
    )
}

const val PORT = 8080
const val HOST = "127.0.0.1"

fun setCryptrEnvManually() {
    System.setProperty(CryptrEnvironment.CRYPTR_TENANT_DOMAIN.toString(), "tenant-domain")
    System.setProperty(CryptrEnvironment.CRYPTR_BASE_URL.toString(), "https://my-company.authent.me")
    System.setProperty(CryptrEnvironment.CRYPTR_DEFAULT_REDIRECT_URL.toString(), "http://localhost:8080/callback")
    System.setProperty(CryptrEnvironment.CRYPTR_API_KEY_CLIENT_ID.toString(), "api-key-id")
    System.setProperty(CryptrEnvironment.CRYPTR_API_KEY_CLIENT_SECRET.toString(), "api-key-secret")
}


fun main() {
    Dotenv.configure().systemProperties().load()
//    setCryptrEnvManually()
    embeddedServer(Netty, port = PORT, host = HOST, module = Application::myApplicationModule).start(wait = true)
}

fun Application.myApplicationModule() {
    val cryptrApiable = CryptrApiable(Cryptr(), "DEBUG")

    routing {
        get("/") {
//            cryptrApiable.createSSOConnection(call)
            call.respondHtml(HttpStatusCode.OK, HTML::index)
        }
        get("/request") {
            cryptrApiable.handleHeadlessRequest(call)
        }
        get("/callback") {
            cryptrApiable.handleHeadlessCallback(call)
        }
        get("/organizations") {
            cryptrApiable.listOrganizations(call)
        }
        get("/create-organization") {
            cryptrApiable.createOrganization(call)
        }
        get("/delete-organization") {
            cryptrApiable.deleteOrganization(call)
        }
        get("/users") {
            cryptrApiable.listUsers(call)
        }
        get("/create-user") {
            cryptrApiable.createUser(call)
        }
        get("/update-user") {
            cryptrApiable.updateUser(call)
        }
        get("/delete-user") {
            cryptrApiable.deleteUser(call)
        }
        get("/applications") {
            cryptrApiable.listApplications(call)
        }
        get("/create-application") {
            cryptrApiable.createApplications(call)
        }
        get("/delete-application") {
            cryptrApiable.deleteteApplications(call)
        }
        post("/create-sso-connection") {
            cryptrApiable.createSSOConnection(call)
        }
        get("/invite-admin-onboarding") {
            cryptrApiable.inviteAdminOnboarding(call)
        }
        get("/admin-onboarding") {
            cryptrApiable.retrieveAdminOnboarding(call)
        }
        get("/reset-admin-onboarding") {
            cryptrApiable.resetAdminOnboarding(call)
        }
        static("/static") {
            resources()
        }
    }
}
