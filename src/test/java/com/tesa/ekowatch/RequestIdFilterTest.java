package com.tesa.ekowatch;

import com.tesa.ekowatch.shared.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {
    @Test
    void echoesValidIdAndRestoresMdcOnFailure() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "demo-123");
        var response = new MockHttpServletResponse();
        MDC.put("requestId", "outer");
        try {
            assertThrows(jakarta.servlet.ServletException.class, () -> new RequestIdFilter().doFilter(request, response, (a, b) -> {
                assertEquals("demo-123", MDC.get("requestId"));
                throw new jakarta.servlet.ServletException("test");
            }));
            assertEquals("demo-123", response.getHeader("X-Request-Id"));
            assertEquals("outer", MDC.get("requestId"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void replacesInvalidIdAndClearsMdc() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "invalid\r\nvalue");
        var response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response, (a, b) -> assertNotNull(MDC.get("requestId")));
        assertDoesNotThrow(() -> java.util.UUID.fromString(response.getHeader("X-Request-Id")));
        assertNull(MDC.get("requestId"));
    }
}

