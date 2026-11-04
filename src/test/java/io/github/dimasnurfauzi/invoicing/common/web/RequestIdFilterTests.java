package io.github.dimasnurfauzi.invoicing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTests {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void generatesIdWhenNoneIsSentAndClearsMdcAfter() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> inMdc = new AtomicReference<>();

    filter.doFilter(
        new MockHttpServletRequest(),
        response,
        (req, res) -> inMdc.set(MDC.get(RequestIdFilter.MDC_KEY)));

    String header = response.getHeader(RequestIdFilter.HEADER);
    assertThat(UUID.fromString(header)).isNotNull();
    assertThat(inMdc.get()).isEqualTo(header);
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void reusesSafeClientId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "ticket-1234");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("ticket-1234");
  }

  @Test
  void replacesUnsafeClientId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "evil\nFAKE LOG LINE");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(RequestIdFilter.HEADER)).doesNotContain("evil");
  }
}
