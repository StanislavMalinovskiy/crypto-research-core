package io.cryptoresearch.signal.infrastructure.web;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class PumpSwapOperatorAccessPolicyTest {
    static MockEnvironment safeEnvironment() {
        return new MockEnvironment().withProperty("server.address", "127.0.0.1");
    }
    static MockHttpServletRequest local(String method) {
        var request = new MockHttpServletRequest(method, "/operator/api/capture");
        request.setLocalAddr("127.0.0.1"); request.setRemoteAddr("127.0.0.1");
        request.addHeader("Host", "127.0.0.1:18081");
        request.addHeader("X-Operator-Request", "local-manual-v1");
        return request;
    }
    @Test void safeStandaloneStartupAndMatchingManualOriginAreAllowed() {
        var policy = new PumpSwapOperatorAccessPolicy(safeEnvironment());
        var request = local("GET"); request.addHeader("Origin", "http://127.0.0.1:18081");
        assertThatCode(() -> policy.check(request, true)).doesNotThrowAnyException();
    }
    @Test void unsafeEnabledStartupRejectsEveryExposureAndCaptureFlag() {
        for (var value : Map.of("server.address", "0.0.0.0", "server.forward-headers-strategy", "framework",
                "crypto.research.marketdata.capture.enabled", "true",
                "crypto.research.marketdata.capture.continuous-enabled", "true").entrySet()) {
            assertThatThrownBy(() -> new PumpSwapOperatorAccessPolicy(safeEnvironment().withProperty(value.getKey(), value.getValue())))
                .isInstanceOf(IllegalStateException.class).hasMessage("OPERATOR_UNSAFE_STARTUP");
        }
        assertThatThrownBy(() -> new PumpSwapOperatorAccessPolicy(new MockEnvironment())).hasMessage("OPERATOR_UNSAFE_STARTUP");
        assertThatThrownBy(() -> new PumpSwapOperatorAccessPolicy(safeEnvironment().withProperty("crypto.research.marketdata.capture.enabled", "invalid"))).hasMessage("OPERATOR_UNSAFE_STARTUP");
    }
    @Test void hostileHeadersAddressesAndMissingManualHeaderRejectBeforeReads() {
        var policy = new PumpSwapOperatorAccessPolicy(safeEnvironment());
        for (var header : Map.of("Host", "attacker.invalid", "Origin", "https://127.0.0.1:18081",
                "Forwarded", "for=127.0.0.1", "X-Forwarded-Anything", "x", "Sec-Fetch-Site", "cross-site").entrySet()) {
            var request = local("GET"); request.removeHeader(header.getKey()); request.addHeader(header.getKey(), header.getValue());
            assertThatThrownBy(() -> policy.check(request, true)).hasMessage("LOCAL_POLICY");
        }
        var peer = local("GET"); peer.setRemoteAddr("::1");
        assertThatThrownBy(() -> policy.check(peer, true)).hasMessage("LOCAL_POLICY");
        var local = local("GET"); local.setLocalAddr("0.0.0.0");
        assertThatThrownBy(() -> policy.check(local, true)).hasMessage("LOCAL_POLICY");
        var missing = local("GET"); missing.removeHeader("X-Operator-Request");
        assertThatThrownBy(() -> policy.check(missing, true)).hasMessage("LOCAL_POLICY");
        var duplicate = local("GET"); duplicate.addHeader("Host", "localhost:18081");
        assertThatThrownBy(() -> policy.check(duplicate, true)).hasMessage("LOCAL_POLICY");
    }
}
