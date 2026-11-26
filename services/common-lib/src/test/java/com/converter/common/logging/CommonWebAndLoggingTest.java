package com.converter.common.logging;

import com.converter.common.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CommonWebAndLoggingTest {

    @Test
    void requestIdFilter_PreservesValidIdAndReplacesInvalidId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter("test-svc");
        FilterChain noop = (req, res) -> {};

        MockHttpServletRequest validReq = new MockHttpServletRequest();
        validReq.addHeader(RequestIdFilter.HEADER_NAME, "req-demo-123:abc");
        MockHttpServletResponse validRes = new MockHttpServletResponse();
        filter.doFilter(validReq, validRes, noop);
        assertThat(validRes.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("req-demo-123:abc");

        MockHttpServletRequest invalidReq = new MockHttpServletRequest();
        invalidReq.addHeader(RequestIdFilter.HEADER_NAME, "bad\r\nid with spaces");
        MockHttpServletResponse invalidRes = new MockHttpServletResponse();
        filter.doFilter(invalidReq, invalidRes, noop);
        assertThat(invalidRes.getHeader(RequestIdFilter.HEADER_NAME))
                .isNotEqualTo("bad\r\nid with spaces")
                .matches("^[A-Za-z0-9._:-]{1,128}$");
    }

    @Test
    void jsonConsoleLayout_RedactsBearerAndSecrets() {
        String redacted = JsonConsoleLayout.redact("Authorization: Bearer eyJabc.def.ghi password=supersecret token:xyz123");
        assertThat(redacted).doesNotContain("eyJabc.def.ghi");
        assertThat(redacted).doesNotContain("supersecret");
        assertThat(redacted).doesNotContain("xyz123");
        assertThat(redacted).contains("[REDACTED]");
    }
}
