package com.geekersjoel237.koracore.auth.adapters.in.security;

import com.geekersjoel237.koracore.auth.application.query.authenticate.AuthenticateQuery;
import com.geekersjoel237.koracore.auth.domain.vo.AccessToken;
import com.geekersjoel237.koracore.auth.domain.vo.AuthenticatedUser;
import com.geekersjoel237.koracore.auth.ports.in.AuthenticateQueryHandler;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final AuthenticateQueryHandler authenticate;

    public JwtAuthenticationFilter(AuthenticateQueryHandler authenticate) {
        this.authenticate = authenticate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthenticatedUser user = authenticate.execute(
                        new AuthenticateQuery(AccessToken.of(header.substring(7))));

                String userId = user.userId().value();
                request.setAttribute("customerId", userId);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (BusinessException | IllegalArgumentException e) {
                log.warn("[JWT] Token validation failed — customerId will not be set: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
