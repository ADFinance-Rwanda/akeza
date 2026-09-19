package com.tenant.management.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
public class SecurityEventLogger {

    public void authenticationFailure(String reason) {
        log.warn("security.auth_failure method={} path={} corr={} subject={} org={} reason={}",
                method(), path(), corr(), subject(), orgHeader(), reason);
    }

    public void authorizationFailure(String reason) {
        log.warn("security.authz_failure method={} path={} corr={} subject={} org={} reason={}",
                method(), path(), corr(), subject(), orgHeader(), reason);
    }

    private String method() {
        HttpServletRequest request = request();
        return request == null ? "-" : request.getMethod();
    }

    private String path() {
        HttpServletRequest request = request();
        return request == null ? "-" : request.getRequestURI();
    }

    private String corr() {
        String fromMdc = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        HttpServletRequest request = request();
        if (request == null) {
            return "-";
        }
        String header = request.getHeader(CorrelationIdFilter.HEADER);
        return header == null || header.isBlank() ? "-" : header;
    }

    private String subject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return "-";
    }

    private String orgHeader() {
        HttpServletRequest request = request();
        if (request == null) {
            return "-";
        }
        String header = request.getHeader("X-Organization-Id");
        return header == null || header.isBlank() ? "-" : header;
    }

    private HttpServletRequest request() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
