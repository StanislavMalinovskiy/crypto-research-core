package io.cryptoresearch.signal.infrastructure.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.*;
import tools.jackson.databind.json.JsonMapper;

class PumpSwapOperatorControllerTest {
    static String query() {
        var q=PumpSwapOperatorProjectionTest.diagnostic().request();var p=PumpSwapOperatorProjectionTest.POOL;
        var fields=new LinkedHashMap<String,String>();fields.put("session",q.session().toString());fields.put("receivedFrom",q.receivedFromInclusive().toString());fields.put("receivedTo",q.receivedToExclusive().toString());fields.put("eventFrom",q.eventFromInclusive().toString());fields.put("eventTo",q.eventToExclusive().toString());fields.put("chain",p.chain());fields.put("pool",p.pool());fields.put("baseMint",p.baseMint());fields.put("quoteMint",p.quoteMint());fields.put("eventEnd",q.eventToExclusive().toString());
        return fields.entrySet().stream().map(e->e.getKey()+"="+java.net.URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
    static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder request) {
        return request.header("Host","127.0.0.1:18081").header("X-Operator-Request","local-manual-v1").with(r->{r.setLocalAddr("127.0.0.1");r.setRemoteAddr("127.0.0.1");return r;});
    }
    static MockHttpServletRequestBuilder diagnosticPost(String suffix) {
        return post(java.net.URI.create("/operator/api/diagnostic?"+query()+suffix));
    }
    @Test void hostileAndMalformedRequestsNeverCallApis() throws Exception {
        var market=mock(MarketDataApi.class);var signal=mock(SignalApi.class);var mvc=MockMvcBuilders.standaloneSetup(new PumpSwapOperatorController(market,signal,PumpSwapOperatorAccessPolicyTest.safeEnvironment())).build();
        for(var header:Map.of("Origin","http://attacker.invalid","Forwarded","for=127.0.0.1","Sec-Fetch-Site","cross-site").entrySet()) {
            assertThat(mvc.perform(local(get("/operator/api/capture?session="+PumpSwapOperatorProjectionTest.SESSION)).header(header.getKey(),header.getValue())).andReturn().getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(mvc.perform(get("/operator/api/capture?session="+PumpSwapOperatorProjectionTest.SESSION).header("Host","attacker.invalid")).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(local(get("/operator/api/capture?session=x"))).andReturn().getResponse().getStatus()).isEqualTo(400);
        for(var suffix:List.of("&K=2030","&session="+PumpSwapOperatorProjectionTest.SESSION,"&pool="+"x".repeat(257)))
            assertThat(mvc.perform(local(diagnosticPost(suffix))).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(local(diagnosticPost("")).content("body")).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(local(diagnosticPost("")).header("Transfer-Encoding","chunked")).andReturn().getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(market,signal);
    }
    @Test void oneManualReadEachHonestAbsenceSafeErrorsAndHeaders() throws Exception {
        var market=mock(MarketDataApi.class);var signal=mock(SignalApi.class);when(market.captureMonitor(any())).thenReturn(Optional.empty());when(signal.diagnoseRetainedPumpSwapNativePriceRise(any())).thenReturn(PumpSwapOperatorProjectionTest.diagnostic());
        var mvc=MockMvcBuilders.standaloneSetup(new PumpSwapOperatorController(market,signal,PumpSwapOperatorAccessPolicyTest.safeEnvironment())).build();
        var capture=mvc.perform(local(get("/operator/api/capture?session="+PumpSwapOperatorProjectionTest.SESSION))).andReturn().getResponse();
        assertThat(capture.getStatus()).isEqualTo(404);assertThat(capture.getContentAsString()).contains("MONITOR_ABSENT");assertThat(capture.getHeader("Cache-Control")).isEqualTo("no-store");assertThat(capture.getHeader("X-Frame-Options")).isEqualTo("DENY");
        var exchange=mvc.perform(local(diagnosticPost(""))).andReturn();
        assertThat(exchange.getRequest().getQueryString()).as("literal once-encoded manual query").isEqualTo(query());
        var diagnosis=exchange.getResponse();
        assertThat(diagnosis.getStatus()).isEqualTo(200);assertThat(diagnosis.getContentAsString()).contains("INSUFFICIENT","TRIGGER","INDEPENDENT_PANELS");
        verify(market,times(1)).captureMonitor(any());verify(signal,times(1)).diagnoseRetainedPumpSwapNativePriceRise(any());verifyNoMoreInteractions(market,signal);
        when(signal.diagnoseRetainedPumpSwapNativePriceRise(any())).thenThrow(new PumpSwapRetainedWindow.ReadFailure("PRIVATE_SQL_SECRET",new PumpSwapRetainedWindow.Diagnostic(PumpSwapRetainedWindow.Observation.atLeast(4097),PumpSwapRetainedWindow.Observation.unknown())));
        var failure=mvc.perform(local(diagnosticPost(""))).andReturn().getResponse();
        assertThat(failure.getStatus()).isEqualTo(422);assertThat(failure.getContentAsString()).contains("4097","AT_LEAST","UNKNOWN").doesNotContain("PRIVATE_SQL_SECRET");
        doThrow(new IllegalStateException("PRIVATE_FAILURE")).when(signal).diagnoseRetainedPumpSwapNativePriceRise(any());
        var unexpected=mvc.perform(local(diagnosticPost(""))).andReturn().getResponse();assertThat(unexpected.getStatus()).isEqualTo(500);assertThat(unexpected.getContentAsString()).doesNotContain("PRIVATE_FAILURE");
    }
    @Test void busyHasNoSecondApiCallAndPermitReleasesAfterFailure() throws Exception {
        var market=mock(MarketDataApi.class);var signal=mock(SignalApi.class);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(signal.diagnoseRetainedPumpSwapNativePriceRise(any())).thenAnswer(i->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("finite fixture barrier");throw new IllegalStateException("PRIVATE_FAILURE");});
        var mvc=MockMvcBuilders.standaloneSetup(new PumpSwapOperatorController(market,signal,PumpSwapOperatorAccessPolicyTest.safeEnvironment())).build();
        try(var executor=Executors.newSingleThreadExecutor()) {
            var first=executor.submit(()->mvc.perform(local(diagnosticPost(""))).andReturn().getResponse().getStatus());
            try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThat(mvc.perform(local(diagnosticPost(""))).andReturn().getResponse().getStatus()).isEqualTo(429);verify(signal,times(1)).diagnoseRetainedPumpSwapNativePriceRise(any());}
            finally{release.countDown();}
            assertThat(first.get(5,TimeUnit.SECONDS)).isEqualTo(500);
        }
        doReturn(PumpSwapOperatorProjectionTest.diagnostic()).when(signal).diagnoseRetainedPumpSwapNativePriceRise(any());
        assertThat(mvc.perform(local(diagnosticPost(""))).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
    @Test void resourceAssetsAreManualTextOnlyAndSecurityHeadersApply() throws Exception {
        var market=mock(MarketDataApi.class);var signal=mock(SignalApi.class);var mvc=MockMvcBuilders.standaloneSetup(new PumpSwapOperatorController(market,signal,PumpSwapOperatorAccessPolicyTest.safeEnvironment())).build();
        var html=mvc.perform(local(get("/operator"))).andReturn().getResponse();var script=mvc.perform(local(get("/operator/operator.js"))).andReturn().getResponse();
        assertThat(html.getStatus()).isEqualTo(200);assertThat(script.getStatus()).isEqualTo(200);assertThat(html.getContentAsByteArray().length).isLessThanOrEqualTo(65536);assertThat(script.getContentAsByteArray().length).isLessThanOrEqualTo(65536);
        assertThat(html.getContentAsString()).contains("/operator/operator.js","INSUFFICIENT","DIAGNOSTIC_ONLY_NOT_ADMITTED").doesNotContain("<script>","onclick=","value=");
        assertThat(script.getContentAsString()).contains("textContent","URLSearchParams","submit","local-manual-v1").doesNotContain("innerHTML","outerHTML","insertAdjacentHTML","eval(","setInterval","setTimeout","Number(","parseFloat","parseInt","DOMContentLoaded");
        assertThat(html.getHeader("Content-Security-Policy")).contains("default-src 'none'","frame-ancestors 'none'","form-action 'none'");verifyNoInteractions(market,signal);
    }
    @Test void oversizeProjectionReturnsComplete422NotPartialMonitor() throws Exception {
        var market=mock(MarketDataApi.class);var signal=mock(SignalApi.class);when(market.captureMonitor(any())).thenReturn(Optional.of(PumpSwapOperatorProjectionTest.monitor("Ж".repeat(2049))));
        var mvc=MockMvcBuilders.standaloneSetup(new PumpSwapOperatorController(market,signal,PumpSwapOperatorAccessPolicyTest.safeEnvironment())).build();var response=mvc.perform(local(get("/operator/api/capture?session="+PumpSwapOperatorProjectionTest.SESSION))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(422);assertThat(response.getContentAsString()).contains("OUTPUT_LIMIT").doesNotContain("MONITOR_PRESENT","Ж");verify(market,times(1)).captureMonitor(any());
    }
}
