package io.cryptoresearch.signal.infrastructure.web;

import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;

/** Local presentation is not public authentication or proxy trust. */
final class PumpSwapOperatorAccessPolicy {
    PumpSwapOperatorAccessPolicy(Environment environment) {
        if (!"127.0.0.1".equals(environment.getProperty("server.address"))
                || !"none".equals(environment.getProperty("server.forward-headers-strategy", "none"))
                || !"false".equals(environment.getProperty("crypto.research.marketdata.capture.enabled", "false"))
                || !"false".equals(environment.getProperty("crypto.research.marketdata.capture.continuous-enabled", "false"))) {
            throw new IllegalStateException("OPERATOR_UNSAFE_STARTUP");
        }
    }

    void check(HttpServletRequest request, boolean json) {
        if (!"127.0.0.1".equals(request.getLocalAddr()) || !"127.0.0.1".equals(request.getRemoteAddr())
                || !"http".equals(request.getScheme())) reject();
        for (var name : Collections.list(request.getHeaderNames())) {
            var lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("forwarded") || lower.startsWith("x-forwarded-")) reject();
        }
        var host = single(request, "Host", true);
        if (!host.matches("(?i)(127\\.0\\.0\\.1|localhost)(:[0-9]{1,5})?")) reject();
        if (host.contains(":")) {
            int port = Integer.parseInt(host.substring(host.indexOf(':') + 1));
            if (port < 1 || port > 65535) reject();
        }
        var origin = single(request, "Origin", false);
        if (origin != null) {
            try {
                var uri = URI.create(origin);
                if (!origin.equalsIgnoreCase("http://" + host) || uri.getUserInfo() != null
                        || uri.getRawQuery() != null || uri.getFragment() != null) reject();
            } catch (IllegalArgumentException invalid) { reject(); }
        }
        if ("cross-site".equalsIgnoreCase(single(request, "Sec-Fetch-Site", false))) reject();
        if (json && !"local-manual-v1".equals(single(request, "X-Operator-Request", true))) reject();
    }

    private static String single(HttpServletRequest request, String name, boolean required) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() > 1 || required && values.isEmpty()) reject();
        return values.isEmpty() ? null : values.getFirst();
    }

    private static void reject() { throw new IllegalArgumentException("LOCAL_POLICY"); }
}
