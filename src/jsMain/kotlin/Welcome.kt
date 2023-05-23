import csstype.Display
import csstype.px
import csstype.rgb
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.InputType
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.useState

external interface WelcomeProps : Props {
    var name: String
    var orgDomain: String
    var userEmail: String
}

val Welcome = FC<WelcomeProps> { props ->
    var name by useState(props.name)
    var orgDomain by useState(props.orgDomain)
    var userEmail by useState(props.userEmail)

    div {
        css {
            padding = 5.px
            backgroundColor = rgb(8, 97, 22)
            color = rgb(56, 246, 137)
        }
        +"Bonjour, $name"
    }
    div {
        div {
            input {
                css {
                    marginTop = 5.px
                    marginBottom = 5.px
                    fontSize = 14.px
                    display = Display.inline
                }
                type = InputType.text
                value = orgDomain
                onChange = { event ->
                    orgDomain = event.target.value
                }
            }
            a {
                href = "/request?org_domain=$orgDomain"
                css {
                    width = 25.px
                    height = 25.px
                    display = Display.block
                    backgroundColor = rgb(42, 42, 42)
                }
            }
        }
        div {
            input {
                type = InputType.email
                value = userEmail
                onChange = { event ->
                    userEmail = event.target.value
                }
                css {
                    marginTop = 5.px
                    marginBottom = 5.px
                    fontSize = 14.px
                    display = Display.inline
                }
            }
            a {
                href = "/request?user_email=$userEmail"
                css {
                    width = 25.px
                    height = 25.px
                    display = Display.block
                    backgroundColor = rgb(24, 24, 24)
                }
            }
        }
    }


}