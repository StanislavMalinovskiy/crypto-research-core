package io.cryptoresearch.marketdata.application;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings;
import tools.jackson.databind.json.JsonMapper;

class PumpSwapContinuousCaptureTest {
	@TempDir Path directory;
	@Test void exactResourceAdditionAndAllSevenIndependentDimensionsCannotOverflowOrDisappear() {
		var all=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Resources(1,2,3,4,5,6,7);
		assertThat(all.plus(all)).isEqualTo(new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Resources(2,4,6,8,10,12,14));
		for(var missing:java.util.List.of(new long[]{2,2,3,4,5,6,7},new long[]{1,3,3,4,5,6,7},new long[]{1,2,4,4,5,6,7},
				new long[]{1,2,3,5,5,6,7},new long[]{1,2,3,4,6,6,7},new long[]{1,2,3,4,5,7,7},new long[]{1,2,3,4,5,6,8}))
			assertThat(all.contains(new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Resources(missing[0],missing[1],missing[2],missing[3],missing[4],missing[5],missing[6]))).isFalse();
		var maximum=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Resources(Long.MAX_VALUE,0,0,0,0,0,0);
		assertThatThrownBy(()->maximum.plus(all)).isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(()->new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Resources(0,0,0,0,0,0,-1)).hasMessage("NEGATIVE_DEPLOYMENT_RESOURCES");
	}
	@Test void immutableSegmentIdentitySeparatesDisplayBoundariesAndKeepsExactCostAuthority() {
		assertThat(ContinuousCaptureLedgerUseCase.sessionId("a|b",1)).isNotEqualTo(ContinuousCaptureLedgerUseCase.sessionId("a",1));
		assertThat(ContinuousCaptureLedgerUseCase.sessionId("a",1)).isNotEqualTo(ContinuousCaptureLedgerUseCase.sessionId("a",2));
		assertThat(ContinuousCaptureLedgerUseCase.sessionId("a",1)).isEqualTo(ContinuousCaptureLedgerUseCase.sessionId("a",1));
		var c=PumpSwapPoolCaptureTest.configuration(PumpSwapPoolCaptureTest.SESSION,0L);
		assertThat(ContinuousCaptureLedgerUseCase.resources(c).reservedHundredths()).isEqualTo(CaptureBudget.reservation(c));
	}
	@Test void deploymentConfigurationCanonicalizesPoolOrderAndMicrosecondOriginalStart() {
		var c=PumpSwapPoolCaptureTest.configuration(PumpSwapPoolCaptureTest.SESSION,0L);var pools=new java.util.ArrayList<>(c.pools());java.util.Collections.reverse(pools);
		var alternate=new Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt().plusNanos(1),pools,c.limits(),c.authority());
		var r=ContinuousCaptureLedgerUseCase.resources(c);
		var one=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration("same","continuous-capture-v1",c,r,r,c.startsAt().plusSeconds(600),1,180,1,60,1024,1000);
		var two=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration("same","continuous-capture-v1",alternate,r,r,c.startsAt().plusSeconds(600),1,180,1,60,1024,1000);
		assertThat(two).as("canonical frozen configuration must not change for equivalent pool ordering or sub-microsecond input").isEqualTo(one);
	}
	@Test void unknownOpeningUsageAndUnboundedReceiptsOrPollCannotBecomeAuthority() {
		var c=PumpSwapPoolCaptureTest.configuration(PumpSwapPoolCaptureTest.SESSION,0L);
		var r=ContinuousCaptureLedgerUseCase.resources(c);
		assertThatThrownBy(()->new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration("test","continuous-capture-v1",c,r,null,c.startsAt().plusSeconds(600),1,180,1,60,1024,1000)).hasMessage("UNKNOWN_DEPLOYMENT_OPENING_USAGE");
		for(var poll:java.util.List.of(0L,10001L))assertThatThrownBy(()->new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration("test","continuous-capture-v1",c,r,r,c.startsAt().plusSeconds(600),1,180,1,60,1024,poll)).hasMessage("INVALID_DEPLOYMENT_FINITE_LIMITS");
		assertThatThrownBy(()->new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration("test","continuous-capture-v1",c,r,r,c.startsAt().plusSeconds(600),1,180,2,60,1024,1000)).hasMessage("INVALID_DEPLOYMENT_FINITE_LIMITS");
	}

	@Test void simultaneousLegacyAndContinuousModesNeverStartEitherCapture() throws Exception {
		var settings=new PumpSwapCaptureSettings();
		settings.setEnabled(true);
		settings.setContinuousEnabled(true);
		var file=directory.resolve("legacy.json");
		Files.writeString(file,JsonMapper.builder().build().writeValueAsString(
				PumpSwapPoolCaptureTest.configuration(PumpSwapPoolCaptureTest.SESSION,0L)));
		settings.setConfigurationFile(file.toString());
		settings.setContinuousConfigurationFile(directory.resolve("continuous.json").toString());
		var collector=mock(PumpSwapPoolCollector.class);
		new PumpSwapCaptureLauncher(settings,collector,JsonMapper.builder().build())
				.run(new DefaultApplicationArguments());
		verify(collector,never()).start(any(Configuration.class));
	}

	@Test void disabledLauncherNeverReadsAnyConfigurationOrStartsCapture() {
		var settings=new PumpSwapCaptureSettings();
		settings.setConfigurationFile(directory.resolve("absent.json").toString());
		settings.setContinuousConfigurationFile(directory.resolve("absent-continuous.json").toString());
		var collector=mock(PumpSwapPoolCollector.class);
		var json=mock(tools.jackson.databind.ObjectMapper.class);
		new PumpSwapCaptureLauncher(settings,collector,json).run(new DefaultApplicationArguments());
		verifyNoInteractions(collector,json);
	}
}
