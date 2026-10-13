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
	private org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous;
	private org.springframework.beans.factory.ObjectProvider<AmendCapturePeriodAuthorityUseCase> amendment;
	private org.springframework.beans.factory.ObjectProvider<EnrollCapturePeriodContinuationUseCase> continuation;
	private org.springframework.beans.factory.ObjectProvider<EnrollCaptureSamePeriodReplacementUseCase> replacement;
	@org.springframework.beans.factory.annotation.Autowired
	public PumpSwapCaptureLauncher(PumpSwapCaptureSettings settings,PumpSwapPoolCollector collector,ObjectMapper json,
			org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous,
			org.springframework.beans.factory.ObjectProvider<AmendCapturePeriodAuthorityUseCase> amendment,
			org.springframework.beans.factory.ObjectProvider<EnrollCapturePeriodContinuationUseCase> continuation,
			org.springframework.beans.factory.ObjectProvider<EnrollCaptureSamePeriodReplacementUseCase> replacement) {
		this(settings,collector,json,continuous,amendment,continuation);this.replacement=replacement;
	}
	public PumpSwapCaptureLauncher(PumpSwapCaptureSettings settings,PumpSwapPoolCollector collector,ObjectMapper json,
			org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous,
			org.springframework.beans.factory.ObjectProvider<AmendCapturePeriodAuthorityUseCase> amendment,
			org.springframework.beans.factory.ObjectProvider<EnrollCapturePeriodContinuationUseCase> continuation) {
		this(settings,collector,json,continuous,amendment);this.continuation=continuation;
	}
	public PumpSwapCaptureLauncher(PumpSwapCaptureSettings settings,PumpSwapPoolCollector collector,ObjectMapper json,
			org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous,
			org.springframework.beans.factory.ObjectProvider<AmendCapturePeriodAuthorityUseCase> amendment) {
		this(settings,collector,json,continuous);this.amendment=amendment;
	}
	public PumpSwapCaptureLauncher(PumpSwapCaptureSettings settings,PumpSwapPoolCollector collector,ObjectMapper json,
			org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous) {
		this(settings,collector,json);this.continuous=continuous;
	}
	public PumpSwapCaptureLauncher(PumpSwapCaptureSettings settings,PumpSwapPoolCollector collector,ObjectMapper json) {
		this.settings=settings; this.collector=collector; this.json=json;
	}
	@Override public void run(ApplicationArguments arguments) {
		if(!settings.isEnabled()) return;
		String reason;
		try {
			if(present(settings.getSamePeriodReplacementFile()) && (!settings.isContinuousEnabled() || present(settings.getAuthorityAmendmentFile())
				|| present(settings.getPeriodContinuationFile()) || present(settings.getConfigurationFile())))throw new IllegalStateException("AMBIGUOUS_CAPTURE_LAUNCH_MODE");
			if(settings.getPeriodContinuationFile()!=null && !settings.getPeriodContinuationFile().isBlank()
				&& (!settings.isContinuousEnabled() || (settings.getAuthorityAmendmentFile()!=null&&!settings.getAuthorityAmendmentFile().isBlank())))
				throw new IllegalStateException("AMBIGUOUS_CAPTURE_LAUNCH_MODE");
			if(settings.isContinuousEnabled() && settings.getConfigurationFile()!=null && !settings.getConfigurationFile().isBlank())
				throw new IllegalStateException("AMBIGUOUS_CAPTURE_LAUNCH_MODE");
			if(settings.isContinuousEnabled()) {
				if(settings.getContinuousConfigurationFile()==null || settings.getContinuousConfigurationFile().isBlank())throw new IllegalStateException("MISSING_CONTINUOUS_CONFIGURATION_FILE");
				var path=Path.of(settings.getContinuousConfigurationFile());
				if(Files.size(path)>1000000)throw new IllegalStateException("CAPTURE_CONFIGURATION_BYTE_LIMIT");
				var configuration=json.readValue(Files.readString(path),io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration.class);
				if(continuous==null)throw new IllegalStateException("CONTINUOUS_CAPTURE_UNAVAILABLE");
				applyAmendment();
				applyContinuation(configuration);
				applyReplacement(configuration);
				continuous.getObject().start(configuration);reason="CONTINUOUS_CAPTURE_STARTED";
				LoggerFactory.getLogger(PumpSwapCaptureLauncher.class).info("PumpSwap capture startup {}",reason);return;
			}
			if(settings.getConfigurationFile()==null || settings.getConfigurationFile().isBlank()) throw new IllegalStateException("MISSING_CAPTURE_CONFIGURATION_FILE");
			var path=Path.of(settings.getConfigurationFile());
			if(Files.size(path)>1000000) throw new IllegalStateException("CAPTURE_CONFIGURATION_BYTE_LIMIT");
			var configuration=json.readValue(Files.readString(path),Configuration.class);
			applyAmendment();
			var status=collector.start(configuration); reason=status.state()+":"+status.reason();
		} catch(Exception invalid) { reason="BLOCKED_FOR_LAUNCH:"+io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.safeReason(invalid); }
		LoggerFactory.getLogger(PumpSwapCaptureLauncher.class).info("PumpSwap capture startup {}",reason);
	}
	private static boolean present(String file) {return file!=null&&!file.isBlank();}
	private void applyReplacement(io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration configuration) throws java.io.IOException {
		var file=settings.getSamePeriodReplacementFile();if(!present(file))return;
		if(replacement==null)throw new IllegalStateException("SAME_PERIOD_REPLACEMENT_UNAVAILABLE");
		var path=Path.of(file);if(Files.size(path)>1000000)throw new IllegalStateException("CAPTURE_CONFIGURATION_BYTE_LIMIT");
		var text=Files.readString(path);var secret=settings.getSecret();
		if(secret!=null&&!secret.isBlank()&&text.contains(secret))throw new IllegalStateException("SECRET_IN_CAPTURE_CONFIGURATION");
		var r=json.readValue(text,io.cryptoresearch.marketdata.api.PumpSwapCaptureSamePeriodReplacement.class);
		if(secret!=null&&!secret.isBlank()&&decodedSecret(json.readTree(json.writeValueAsString(r)),secret))throw new IllegalStateException("SECRET_IN_CAPTURE_CONFIGURATION");
		if(!configuration.equals(r.successor()))throw new IllegalStateException("REPLACEMENT_CONFIGURATION_CONFLICT");
		replacement.getObject().enroll(r);
	}
	private void applyContinuation(io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration configuration) throws java.io.IOException {
		var file=settings.getPeriodContinuationFile();if(file==null||file.isBlank())return;
		if(continuation==null)throw new IllegalStateException("PERIOD_CONTINUATION_UNAVAILABLE");
		var path=Path.of(file);if(Files.size(path)>1000000)throw new IllegalStateException("CAPTURE_CONFIGURATION_BYTE_LIMIT");
		var text=Files.readString(path);var secret=settings.getSecret();
		if(secret!=null&&!secret.isBlank()&&text.contains(secret))throw new IllegalStateException("SECRET_IN_CAPTURE_CONFIGURATION");
		var r=json.readValue(text,io.cryptoresearch.marketdata.api.PumpSwapCapturePeriodContinuation.class);
		if(secret!=null&&!secret.isBlank()&&decodedSecret(json.readTree(json.writeValueAsString(r)),secret))throw new IllegalStateException("SECRET_IN_CAPTURE_CONFIGURATION");
		if(!configuration.equals(r.successor()))throw new IllegalStateException("CONTINUATION_CONFIGURATION_CONFLICT");
		continuation.getObject().enroll(r);
	}
	private void applyAmendment() throws java.io.IOException {
		var file=settings.getAuthorityAmendmentFile();if(file==null || file.isBlank())return;
		if(amendment==null)throw new IllegalStateException("CAPTURE_AUTHORITY_AMENDMENT_UNAVAILABLE");
		var path=Path.of(file);if(Files.size(path)>1000000)throw new IllegalStateException("CAPTURE_CONFIGURATION_BYTE_LIMIT");
		var text=Files.readString(path);
		if(settings.getSecret()!=null && !settings.getSecret().isBlank() && text.contains(settings.getSecret()))throw new IllegalStateException("SECRET_IN_CAPTURE_CONFIGURATION");
		var request=json.readValue(text,io.cryptoresearch.marketdata.api.PumpSwapCaptureAuthorityAmendment.class);
		if(settings.getSecret()!=null && !settings.getSecret().isBlank()
			&& decodedSecret(json.readTree(json.writeValueAsString(request)),settings.getSecret()))throw new IllegalStateException("SECRET_IN_CAPTURE_CONFIGURATION");
		amendment.getObject().amend(request);
	}
	/** Check only the bounded typed values that would be persisted, after JSON unescaping. */
	private static boolean decodedSecret(tools.jackson.databind.JsonNode node,String secret) {
		if(node.isTextual() && node.asText().contains(secret))return true;
		for(var value:node)if(decodedSecret(value,secret))return true;
		return false;
	}
}
