package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.PumpSwapCaptureSamePeriodReplacement;

class CaptureSamePeriodReplacementTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    static PumpSwapCaptureSamePeriodReplacement request() {
        var at=PumpSwapPoolCaptureTest.START;
        var root=CapturePeriodContinuationIT.configuration("replacement-unit",at,at.plusSeconds(86400),0,0);
        var next=CaptureSamePeriodReplacementIT.child(root,"replacement-unit-child",root.opening(),0,at);
        return new PumpSwapCaptureSamePeriodReplacement("continuous-same-period-replacement-v1","root","sha256:"+"a".repeat(64),"QUEUE_OVERFLOW","root","sha256:"+"a".repeat(64),next,next.opening(),0,"owner-decision-v1","explicit-owner");
    }
    @Test void explicitKnownCarryNeverSubstitutesUnknownZeroOrUnnamedDecision() {
        var r=request();assertThatCode(()->EnrollCaptureSamePeriodReplacementUseCase.validate(r)).doesNotThrowAnyException();
        var missing=new PumpSwapCaptureSamePeriodReplacement(r.version(),r.predecessorDeploymentKey(),r.predecessorConfigurationDigest(),r.predecessorReason(),r.allocationRootKey(),r.allocationRootConfigurationDigest(),r.successor(),null,r.consumedSegments(),r.decisionVersion(),r.ownerDecision());
        assertThatThrownBy(()->EnrollCaptureSamePeriodReplacementUseCase.validate(missing)).hasMessage("INVALID_SAME_PERIOD_REPLACEMENT");
        assertThatThrownBy(()->EnrollCaptureSamePeriodReplacementUseCase.validate(null)).hasMessage("INVALID_SAME_PERIOD_REPLACEMENT");
    }
    @Test void defaultAbsentAndDisabledReplacementReadsAndResolvesNothing() {
        var settings=new io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings();assertThat(settings.getSamePeriodReplacementFile()).isNull();
        settings.setSamePeriodReplacementFile("missing-never-read.json");
        var collector=org.mockito.Mockito.mock(PumpSwapPoolCollector.class);var json=org.mockito.Mockito.mock(tools.jackson.databind.ObjectMapper.class);
        new PumpSwapCaptureLauncher(settings,collector,json).run(new org.springframework.boot.DefaultApplicationArguments());
        org.mockito.Mockito.verifyNoInteractions(collector,json);
    }
    static PumpSwapCaptureSamePeriodReplacement decision(PumpSwapCaptureSamePeriodReplacement r,String decision) {
        return new PumpSwapCaptureSamePeriodReplacement(r.version(),r.predecessorDeploymentKey(),r.predecessorConfigurationDigest(),r.predecessorReason(),r.allocationRootKey(),r.allocationRootConfigurationDigest(),r.successor(),r.carried(),r.consumedSegments(),r.decisionVersion(),decision);
    }
    @Test @SuppressWarnings("unchecked") void enrollmentPrecedesCoordinatorAndInvalidFileModesNeverResolveEnrollmentOrStart() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var r=request();
        var settings=new io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings();settings.setEnabled(true);settings.setContinuousEnabled(true);settings.setSecret("synthetic-replacement-key");
        var config=directory.resolve("config.json");var file=directory.resolve("replacement.json");java.nio.file.Files.writeString(config,json.writeValueAsString(r.successor()));java.nio.file.Files.writeString(file,json.writeValueAsString(r));
        settings.setContinuousConfigurationFile(config.toString());settings.setSamePeriodReplacementFile(file.toString());
        var collector=org.mockito.Mockito.mock(PumpSwapPoolCollector.class);var coordinator=org.mockito.Mockito.mock(PumpSwapContinuousCaptureCoordinator.class);var enroll=org.mockito.Mockito.mock(EnrollCaptureSamePeriodReplacementUseCase.class);
        org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.springframework.beans.factory.ObjectProvider<EnrollCaptureSamePeriodReplacementUseCase> provider=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(continuous.getObject()).thenReturn(coordinator);org.mockito.Mockito.when(provider.getObject()).thenReturn(enroll);
        var launcher=new PumpSwapCaptureLauncher(settings,collector,json,continuous,null,null,provider);launcher.run(new org.springframework.boot.DefaultApplicationArguments());
        var order=org.mockito.Mockito.inOrder(enroll,coordinator);order.verify(enroll).enroll(r);order.verify(coordinator).start(r.successor());
        for(var mode:java.util.List.of("LITERAL","DECODED","OVERSIZE","MISMATCH","MANUAL","AMENDMENT","CONTINUATION")) {
            org.mockito.Mockito.clearInvocations(enroll,coordinator,continuous,provider,collector);settings.setContinuousEnabled(true);settings.setAuthorityAmendmentFile(null);settings.setPeriodContinuationFile(null);
            java.nio.file.Files.writeString(file,json.writeValueAsString(r));java.nio.file.Files.writeString(config,json.writeValueAsString(r.successor()));
            if(mode.equals("LITERAL") || mode.equals("DECODED")) {
                var text=json.writeValueAsString(decision(r,settings.getSecret()));
                if(mode.equals("DECODED"))text=text.replace(settings.getSecret(),"\\"+"u0073"+settings.getSecret().substring(1));
                java.nio.file.Files.writeString(file,text);
            }
            if(mode.equals("OVERSIZE"))java.nio.file.Files.writeString(file," ".repeat(1000001));
            if(mode.equals("MISMATCH"))java.nio.file.Files.writeString(config,json.writeValueAsString(CaptureSamePeriodReplacementIT.child(r.successor(),"wrong-loaded-child",r.carried(),0,r.successor().template().startsAt())));
            if(mode.equals("MANUAL"))settings.setContinuousEnabled(false);
            if(mode.equals("AMENDMENT"))settings.setAuthorityAmendmentFile("missing-amendment");
            if(mode.equals("CONTINUATION"))settings.setPeriodContinuationFile("missing-continuation");
            launcher.run(new org.springframework.boot.DefaultApplicationArguments());org.mockito.Mockito.verifyNoInteractions(enroll,coordinator,provider,continuous,collector);
        }
        settings.setContinuousEnabled(true);settings.setPeriodContinuationFile(null);settings.setSamePeriodReplacementFile(null);java.nio.file.Files.writeString(config,json.writeValueAsString(r.successor()));
        launcher.run(new org.springframework.boot.DefaultApplicationArguments());org.mockito.Mockito.verify(coordinator).start(r.successor());org.mockito.Mockito.verifyNoInteractions(enroll,provider,collector);
    }
    @Test @SuppressWarnings("unchecked") void omittedZeroValuedCarryOrCountProofNeverBecomesAnExplicitKnownZeroAtEnrollment() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var r=request();var exact=json.writeValueAsString(r);
        assertThat(exact).contains("\"carried\":{\"requests\":0,","\"consumedSegments\":0,");
        var settings=new io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings();settings.setEnabled(true);settings.setContinuousEnabled(true);
        var config=directory.resolve("known-config.json");var file=directory.resolve("unknown-proof.json");java.nio.file.Files.writeString(config,json.writeValueAsString(r.successor()));
        settings.setContinuousConfigurationFile(config.toString());settings.setSamePeriodReplacementFile(file.toString());
        var collector=org.mockito.Mockito.mock(PumpSwapPoolCollector.class);var coordinator=org.mockito.Mockito.mock(PumpSwapContinuousCaptureCoordinator.class);var enroll=org.mockito.Mockito.mock(EnrollCaptureSamePeriodReplacementUseCase.class);
        org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.springframework.beans.factory.ObjectProvider<EnrollCaptureSamePeriodReplacementUseCase> provider=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(continuous.getObject()).thenReturn(coordinator);org.mockito.Mockito.when(provider.getObject()).thenReturn(enroll);
        var launcher=new PumpSwapCaptureLauncher(settings,collector,json,continuous,null,null,provider);
        assertThat(json.readValue(exact,PumpSwapCaptureSamePeriodReplacement.class)).isEqualTo(r);
        java.nio.file.Files.writeString(file,exact);launcher.run(new org.springframework.boot.DefaultApplicationArguments());
        org.mockito.Mockito.verify(enroll).enroll(r);org.mockito.Mockito.verify(coordinator).start(r.successor());
        org.mockito.Mockito.clearInvocations(enroll,provider,coordinator,continuous,collector);
        for(var incomplete:java.util.List.of(exact.replace("\"carried\":{\"requests\":0,","\"carried\":{"),exact.replace("\"consumedSegments\":0,",""))) {
            var missing=incomplete.contains("\"carried\":{\"requests\":0,")?"consumedSegments":"requests";
            assertThatThrownBy(()->json.readValue(incomplete,PumpSwapCaptureSamePeriodReplacement.class)).isInstanceOf(tools.jackson.databind.exc.MismatchedInputException.class).hasMessageContaining(missing).hasMessageContaining("FAIL_ON_NULL_FOR_PRIMITIVES");
            java.nio.file.Files.writeString(file,incomplete);launcher.run(new org.springframework.boot.DefaultApplicationArguments());
            org.mockito.Mockito.verifyNoInteractions(enroll,provider,coordinator,continuous,collector);
        }
    }
}
