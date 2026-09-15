package com.byteforge.medihive.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
    @Bean UserDetailsService users(PasswordEncoder encoder){
        var store=new InMemoryUserDetailsManager();
        for(DemoAccounts.Account account:DemoAccounts.ALL)
            store.createUser(User.withUsername(account.username()).password(encoder.encode(account.password())).roles(account.role()).build());
        return store;
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a->a.requestMatchers("/login","/styles.css","/health").permitAll()
            .requestMatchers("/h2-console/**").denyAll().anyRequest().authenticated())
            .formLogin(f->f.defaultSuccessUrl("/",true)).logout(l->l.logoutSuccessUrl("/login?logout"))
            .csrf(c->c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));
        return http.build();
    }
}
