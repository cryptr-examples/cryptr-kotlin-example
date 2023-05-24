# Cryptr Kotlin Example

This kotlin implementation consume `co.cryptr:cryptr-kotlin:VERSION` in a kTor example

## Installation

First step is to import the dependency

### Gradle(Kotlin)

```kotlin
implementation("co.cryptr:cryptr-kotlin:0.1.2")
```

### Gradle(short)

```java
implementation'co.cryptr:cryptr-kotlin:0.1.2'
```

## Configuration

The Cryptr Kotlin SDK can be either configured with chosen parameter, directly when instantiate or through System
properties

```kotlin
import cryptr.kotlin.Cryptr

// if you use system properties you call just init like this
val cryptr = Cryptr()

// If you prefer to define them manually
val cryptr = Cryptr(
    tenantDomain = "my-saas-company",
    apiKeyClientId = "api-key-id",
    apiKeyClientSecret = "api-key-secret"
)

// You can also specify your cryptr service url
val cryptr = Cryptr(
    tenantDomain = "my-saas-company",
    baseUrl = "https://my-saas-company.authent.me",
    apiKeyClientId = "api-key-id",
    apiKeyClientSecret = "api-key-secret"
)
```

See [.env example file](.env.example) to see how to configure with a dot env file

> When instantiated, an API Key Token will be generated using provided API Key credentials

## Usage

When instantiated you can start building your SSO Headless process.

### SSO SAML Challenge creation

This process allows you to generate a challenge to start a SSO SAML authent process without using a front-end for the
entire process

```kotlin
// 1. generate a challenge from any point of your app (requires network) and retrieve authorization URL
val ssoSamlChallengePayload =
    cryptr.createSsoSamlChallenge(
        redirectUri = "https://localhost:8080/some-callback-endpoint",
        orgDomain = orgDomain,
        userEmail = userEmail
    )

if (ssoSamlChallengePayload is APISuccess) {
    val authorizationUrl = ssoSamlChallengePayload.value.authorizationUrl
}

// 2. Give this authorization URL to the end-user (ex: by email or just by a redirection)

```

### SSO SAML Challenge validation

When End user succeeded his SSO SAML authentication process it will be redirected to the request redirectUri

```kotlin
// Example with Ktor
import io.ktor.server.routing.*

routing {
    get("/some-callback-endpoint") {
        val challengeValidation = cryptr.validateSsoChallenge(call.parameters.get("code"))
        if (challengeValidation is APISuccess) {
            val endUserAccessToken = challengeValidation.value.accessToken
            // do your session opening process
        } else {
            // manage error
        }
    }
}
```

Fore more examples see the [CryptrApiable](src/jvmMain/kotlin/com.example.application/CryptrApiable.kt)