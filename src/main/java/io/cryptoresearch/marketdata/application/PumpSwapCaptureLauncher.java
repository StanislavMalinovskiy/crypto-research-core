package io.cryptoresearch.marketdata.application;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings;
import tools.jackson.databind.ObjectMapper;

/** Explicitly enabled external configuration starts the existing synchronous use case. */
@Component
public class PumpSwapCaptureLauncher implements ApplicationRunner {
	private final PumpSwapCaptureSettings settings;
	private final PumpSwapPoolCollector collector;
	private final ObjectMapper json;
	public PumpSwapCaptureLauncher(PumpSwapCaptureSettings settings,PumpSwapPoolCollector collector,ObjectMapper json) {
		this.settings=settings; this.collector=collector; this.json=json;
	}
	@Override public void run(ApplicationArguments arguments) {
		if(!settings.isEnabled()) return;
		String reason;
		try {
			if(settings.getConfigurationFile()==null || settings.getConfigurationFile().isBlank()) throw new IllegalStateException("MISSING_CAPTURE_CONFIGURATION_FILE");
			var path=Path.of(settings.getConfigurationFile());
			if(Files.size(path)>1000000) throw new IllegalStateException("CAPTURE_CONFIGURATION_BYTE_LIMIT");
			var configuration=json.readValue(Files.readString(path),Configuration.class);
			var status=collector.start(configuration); reason=status.state()+":"+status.reason();
		} catch(Exception invalid) { reason="BLOCKED_FOR_LAUNCH:"+io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.safeReason(invalid); }
		LoggerFactory.getLogger(PumpSwapCaptureLauncher.class).info("PumpSwap capture startup {}",reason);
	}
}
