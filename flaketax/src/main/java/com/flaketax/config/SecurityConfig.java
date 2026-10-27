
package com.flaketax.config;

import com.flaketax.user.UserRepo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwords() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(UserRepo repo) {
        return handle -> repo.findByHandle(handle)
                .map(user -> User.withUsername(user.getHandle())
                        .password(user.getPass())
                        .roles("USER")
                        .build())
                .orElseThrow(() ->
                        new org.springframework.security.core.userdetails.UsernameNotFoundException(
                                "Unknown user"
                        ));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/", "/index.html", "/app.js",
                                "/style.css", "/privacy.html",
                                "/terms.html", "/error",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST, "/api/users"
                        ).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
