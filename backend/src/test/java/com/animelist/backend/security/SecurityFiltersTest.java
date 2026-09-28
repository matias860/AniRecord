package com.animelist.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.animelist.backend.service.CurrentUserService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.Cookie;

class SecurityFiltersTest {
    @Test void rejectsUnsafeApiRequestsWithoutTheApplicationHeader() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/library/1/progress/increment");
        var response = new MockHttpServletResponse();

        new CsrfRequestFilter().doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test void acceptsUnsafeApiRequestsWithTheApplicationHeader() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/library/1/progress/increment");
        request.addHeader(CsrfRequestFilter.HEADER, "1");
        var response = new MockHttpServletResponse();

        new CsrfRequestFilter().doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test void limitsRepeatedAuthenticationAttempts() throws Exception {
        var filter = new RateLimitFilter();
        MockHttpServletResponse last = null;
        for (int attempt = 0; attempt < 13; attempt++) {
            var request = new MockHttpServletRequest("POST", "/api/auth/local-login");
            request.setRemoteAddr("192.0.2.10");
            last = new MockHttpServletResponse();
            filter.doFilter(request, last, new MockFilterChain());
        }
        assertEquals(429, last.getStatus());
    }

    @Test void limitsRepeatedProfileUploadsPerSession() throws Exception {
        var filter = new RateLimitFilter();
        MockHttpServletResponse last = null;
        for (int attempt = 0; attempt < 13; attempt++) {
            var request = new MockHttpServletRequest("POST", "/api/profile/assets/avatar");
            request.setRemoteAddr("192.0.2.20");
            request.setCookies(new Cookie(CurrentUserService.COOKIE, "a".repeat(64)));
            last = new MockHttpServletResponse();
            filter.doFilter(request, last, new MockFilterChain());
        }
        assertEquals(429, last.getStatus());
    }

    @Test void usesTheVercelClientAddressBeforeAdditionalProxyHops() throws Exception {
        var filter = new RateLimitFilter();
        MockHttpServletResponse last = null;
        for (int attempt = 0; attempt < 13; attempt++) {
            var request = new MockHttpServletRequest("POST", "/api/auth/local-login");
            request.addHeader("X-Forwarded-For", "198.51.100.7, 203.0.113." + attempt);
            last = new MockHttpServletResponse();
            filter.doFilter(request, last, new MockFilterChain());
        }
        assertEquals(429, last.getStatus());
    }
}
