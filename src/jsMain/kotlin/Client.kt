import kotlinx.browser.document
import react.create
import react.dom.client.createRoot

fun main() {
    val container = document.createElement("div")
    document.body!!.appendChild(container)

    val welcome = Welcome.create {
        name = "Company-name"
        orgDomain = "your-company"
        userEmail = "john@doe.co"
    }
    createRoot(container).render(welcome)
}