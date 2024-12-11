package at.asit.apps.terminal_sp.prototype.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.DefaultSecurityFilterChain

@Configuration
class WebSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity): DefaultSecurityFilterChain = http.logout {
        it.logoutSuccessUrl("/")
    }.authorizeHttpRequests {
        it.anyRequest().permitAll()
    }.headers {
        it.frameOptions { it.sameOrigin() }
    }.csrf {
        it.disable() // to allow POST from wallets
    }.build()

}