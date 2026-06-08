package com.linkstore.metadata.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");
        String usernameHeader = request.getHeader("X-User-Name");
        String roleHeader = request.getHeader("X-User-Role");

        // Bypass check if auth endpoint, but set empty context
        if (userIdHeader != null && usernameHeader != null) {
            String role = roleHeader != null ? roleHeader : "USER";
            
            // UserDetails representation to store in authentication principal
            UserPrincipal principal = new UserPrincipal(UUID.fromString(userIdHeader), usernameHeader, role);

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    public static class UserPrincipal {
        private final UUID id;
        private final String username;
        private final String role;

        public UserPrincipal(UUID id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }

        public UUID getId() { return id; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
    }
}
