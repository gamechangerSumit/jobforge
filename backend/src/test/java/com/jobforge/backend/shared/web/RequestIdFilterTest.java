package com.jobforge.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void echoesValidUuid() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, id);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(id);
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE)).isEqualTo(id);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull(); // cleaned up after the request
    }

    @Test
    void replacesInvalidValueWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "not-a-uuid\r\nInjected: 1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String header = response.getHeader(RequestIdFilter.HEADER);
        assertThat(header).isNotEqualTo("not-a-uuid");
        assertThat(UUID.fromString(header)).isNotNull();
    }

    @Test
    void generatesWhenAbsent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
    }
}
