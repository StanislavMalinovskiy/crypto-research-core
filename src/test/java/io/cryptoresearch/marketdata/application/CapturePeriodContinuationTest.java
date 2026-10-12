package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.PumpSwapCapturePeriodContinuation;

class CapturePeriodContinuationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void missingBaselineNeverDefaultsToZero() {
        assertThatThrownBy(()->new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",Instant.now(),null,0L)).hasMessage("UNKNOWN_PERIOD_BASELINE");
        assertThatThrownBy(()->new PumpSwapCapturePeriodContinuation.Baseline("UNKNOWN",Instant.now(),0L,0L)).hasMessage("UNKNOWN_PERIOD_BASELINE");
    }
    @Test void authoritativeBaselineNormalizesMicrosecondsAndKnownZeroIsExplicit() {
        var time=Instant.parse("2026-10-13T00:00:00.123456789Z");
        var b=new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",time,0L,0L);
        assertThat(b.observedAt()).isEqualTo(Instant.parse("2026-10-13T00:00:00.123456Z"));
        assertThat(b.openingHundredths()).isZero();assertThat(b.otherActivityHundredths()).isZero();
    }
    @Test void absentAndDisabledContinuationNeverResolvesFileOrProvider() {
        var settings=new io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings();settings.setPeriodContinuationFile("missing-continuation.json");
        var collector=org.mockito.Mockito.mock(PumpSwapPoolCollector.class);var json=org.mockito.Mockito.mock(tools.jackson.databind.ObjectMapper.class);
        new PumpSwapCaptureLauncher(settings,collector,json).run(new org.springframework.boot.DefaultApplicationArguments());org.mockito.Mockito.verifyNoInteractions(collector,json);
    }
    @Test @SuppressWarnings("unchecked") void enrollmentPrecedesStartAndEscapedSecretMismatchAndManualModeStopAllWork() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var at=Instant.now();var next=CapturePeriodContinuationIT.configuration("unit-next",at,at.plusSeconds(86400),200,1000);
        var request=new PumpSwapCapturePeriodContinuation("continuous-period-continuation-v1","old","sha256:"+"a".repeat(64),at.minusSeconds(60),next,"owner",new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",at,200L,20L));
        var settings=new io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings();settings.setEnabled(true);settings.setContinuousEnabled(true);settings.setSecret("synthetic-key");
        var config=directory.resolve("config.json");var receipt=directory.resolve("receipt.json");java.nio.file.Files.writeString(config,json.writeValueAsString(next));java.nio.file.Files.writeString(receipt,json.writeValueAsString(request));
        settings.setContinuousConfigurationFile(config.toString());settings.setPeriodContinuationFile(receipt.toString());
        var collector=org.mockito.Mockito.mock(PumpSwapPoolCollector.class);var coordinator=org.mockito.Mockito.mock(PumpSwapContinuousCaptureCoordinator.class);var enroll=org.mockito.Mockito.mock(EnrollCapturePeriodContinuationUseCase.class);
        org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.springframework.beans.factory.ObjectProvider<EnrollCapturePeriodContinuationUseCase> provider=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(continuous.getObject()).thenReturn(coordinator);org.mockito.Mockito.when(provider.getObject()).thenReturn(enroll);
        var launcher=new PumpSwapCaptureLauncher(settings,collector,json,continuous,null,provider);launcher.run(new org.springframework.boot.DefaultApplicationArguments());
        var order=org.mockito.Mockito.inOrder(enroll,coordinator);order.verify(enroll).enroll(request);order.verify(coordinator).start(next);
        org.mockito.Mockito.clearInvocations(enroll,coordinator,continuous,provider);
        var secret=new PumpSwapCapturePeriodContinuation(request.version(),request.predecessorDeploymentKey(),request.predecessorConfigurationDigest(),request.periodStart(),next,settings.getSecret(),request.baseline());
        java.nio.file.Files.writeString(receipt,json.writeValueAsString(secret).replace(settings.getSecret(),"\\"+"u0073"+settings.getSecret().substring(1)));
        launcher.run(new org.springframework.boot.DefaultApplicationArguments());org.mockito.Mockito.verifyNoInteractions(enroll,coordinator,provider,collector);
        settings.setContinuousEnabled(false);launcher.run(new org.springframework.boot.DefaultApplicationArguments());org.mockito.Mockito.verifyNoInteractions(enroll,coordinator,provider,collector);
    }
    @Test void exactFiftyDollarAndWssAuthorityGuardsAreIndependentOfValidCaptureLimits() {
        var at=Instant.now();var valid=CapturePeriodContinuationIT.configuration("budget-unit",at,at.plusSeconds(86400),200,1000);
        var request=new PumpSwapCapturePeriodContinuation("continuous-period-continuation-v1","old","sha256:"+"a".repeat(64),at.minusSeconds(60),valid,"owner",new PumpSwapCapturePeriodContinuation.Baseline("OWNER_REPORTED",at,200L,20L));
        assertThat(CaptureBudget.launchProblem(valid.template())).isEmpty();assertThatCode(()->EnrollCapturePeriodContinuationUseCase.validate(request)).doesNotThrowAnyException();
        assertThat(valid.template().authority().ceilingUsd()).isEqualByComparingTo("50");
        for(var changed:java.util.List.of(new java.math.BigDecimal[]{new java.math.BigDecimal("50.01"),new java.math.BigDecimal("0.0002")},
                new java.math.BigDecimal[]{new java.math.BigDecimal("50"),new java.math.BigDecimal("0.0003")})) {
            var t=valid.template();var a=t.authority();
            var authority=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),Long.MAX_VALUE,changed[0],a.usdPerMillionCu(),changed[1],a.ownerAuthorization(),true,true);
            var provisional=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),t.startsAt(),t.pools(),t.limits(),authority);
            var bounded=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Authority(authority.accountPeriod(),authority.periodEnd(),authority.source(),authority.openingHundredths(),authority.otherActivityHundredths(),CaptureBudget.ceiling(provisional),authority.ceilingUsd(),authority.usdPerMillionCu(),authority.subscriptionCuPerByte(),authority.ownerAuthorization(),true,true);
            var template=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration(t.sessionId(),t.version(),t.provider(),t.startsAt(),t.pools(),t.limits(),bounded);
            var c=new io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration(valid.deploymentKey(),valid.version(),template,valid.allowances(),valid.opening(),valid.globalEnd(),valid.maxSegments(),valid.segmentSeconds(),valid.maxPages(),valid.decodeSeconds(),valid.receiptBytes(),valid.pollMillis());
            assertThat(CaptureBudget.launchProblem(template)).as("capture inputs independently satisfy existing exact budget mathematics").isEmpty();
            var invalid=new PumpSwapCapturePeriodContinuation(request.version(),request.predecessorDeploymentKey(),request.predecessorConfigurationDigest(),request.periodStart(),c,request.ownerDecision(),request.baseline());
            assertThatThrownBy(()->EnrollCapturePeriodContinuationUseCase.validate(invalid)).hasMessage("INVALID_PERIOD_BASELINE_OR_AUTHORITY");
        }
    }
}
