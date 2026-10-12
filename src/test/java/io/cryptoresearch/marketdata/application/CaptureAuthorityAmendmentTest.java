package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;
import io.cryptoresearch.marketdata.api.PumpSwapCaptureAuthorityAmendment;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings;

class CaptureAuthorityAmendmentTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    static Configuration old(String period) {
        var c=PumpSwapPoolCaptureTest.configuration(UUID.randomUUID(),0L);var a=c.authority();
        return new Configuration(c.sessionId(),"capture-rpc-reuse-v6",c.provider(),c.startsAt(),c.pools(),c.limits(),
            new Authority(period,a.periodEnd(),a.source(),0L,0,2_857_142_800L,new BigDecimal("15"),new BigDecimal("0.525"),new BigDecimal("0.04"),"old-owner",true,true));
    }
    static Authority successor(Authority a) {
        return new Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),9_523_809_500L,
            new BigDecimal("50"),a.usdPerMillionCu(),new BigDecimal("0.0002"),"prospective-owner",a.retentionAllowed(),a.exclusiveAllocation());
    }
    static PumpSwapCaptureAuthorityAmendment amendment(String period) {
        var a=old(period).authority();return new PumpSwapCaptureAuthorityAmendment("capture-authority-amendment-v1","alchemy",a,successor(a),"owner-2026-10-12");
    }
    @Test void authorizedProspectiveAuthorityIsValidWithoutChangingLegacySerialization() {
        var a=amendment("october");assertThatCode(()->AmendCapturePeriodAuthorityUseCase.validate(a)).doesNotThrowAnyException();
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var c=old("october");
        assertThat(json.readValue(json.writeValueAsString(c),Configuration.class)).isEqualTo(c);
        assertThat(json.writeValueAsString(c)).doesNotContain("amendment");
    }
    @Test void aliasPeriodOrOpeningChangeCannotInventNewAuthority() {
        var a=amendment("october");var s=a.successor();
        for(var changed:java.util.List.of(new Authority("renamed",s.periodEnd(),s.source(),s.openingHundredths(),s.otherActivityHundredths(),s.ceilingHundredths(),s.ceilingUsd(),s.usdPerMillionCu(),s.subscriptionCuPerByte(),s.ownerAuthorization(),true,true),
            new Authority(s.accountPeriod(),s.periodEnd(),s.source(),1L,s.otherActivityHundredths(),s.ceilingHundredths(),s.ceilingUsd(),s.usdPerMillionCu(),s.subscriptionCuPerByte(),s.ownerAuthorization(),true,true)))
            assertThatThrownBy(()->AmendCapturePeriodAuthorityUseCase.validate(new PumpSwapCaptureAuthorityAmendment(a.version(),a.provider(),a.predecessor(),changed,a.ownerDecision())))
                .hasMessage("CAPTURE_AUTHORITY_PREDECESSOR_CONFLICT");
    }
    @Test void unauthorizedRateCeilingAndMissingDecisionReject() {
        var a=amendment("october");var s=a.successor();
        for(var amount:java.util.List.of("50.01","0","-1")) {
            var invalid=new Authority(s.accountPeriod(),s.periodEnd(),s.source(),s.openingHundredths(),s.otherActivityHundredths(),s.ceilingHundredths(),new BigDecimal(amount),s.usdPerMillionCu(),s.subscriptionCuPerByte(),s.ownerAuthorization(),true,true);
            assertThatThrownBy(()->AmendCapturePeriodAuthorityUseCase.validate(new PumpSwapCaptureAuthorityAmendment(a.version(),a.provider(),a.predecessor(),invalid,a.ownerDecision()))).hasMessage("INVALID_PROSPECTIVE_AUTHORITY");
        }
        assertThatThrownBy(()->AmendCapturePeriodAuthorityUseCase.validate(new PumpSwapCaptureAuthorityAmendment(a.version(),a.provider(),a.predecessor(),s,""))).hasMessage("INVALID_PROSPECTIVE_AUTHORITY");
    }
    @Test void unchangedV6FractionalAccountingAndExactFiftyDollarCeiling() {
        var old=old("october");var amended=new Configuration(old.sessionId(),old.version(),old.provider(),old.startsAt(),old.pools(),old.limits(),successor(old.authority()));
        assertThat(CaptureBudget.ceiling(amended)).isEqualTo(9_523_809_500L);
        assertThat(CaptureBudget.cost(9_523_809_500L,new BigDecimal("0.525"))).isLessThanOrEqualTo(new BigDecimal("50"));
        assertThat(CaptureBudget.cost(9_523_809_600L,new BigDecimal("0.525"))).isGreaterThan(new BigDecimal("50"));
        assertThat(CaptureBudget.subscriptionHundredths(amended,1)).isEqualTo(1);
        assertThat(CaptureBudget.subscriptionHundredths(amended,50)).isEqualTo(1);
        assertThat(CaptureBudget.subscriptionHundredths(amended,51)).isEqualTo(2);
        assertThat(CaptureBudget.subscriptionHundredths(old,50)).isEqualTo(200);
    }
    @Test void disabledLauncherDoesNotReadFileResolveAuthorityOrStartWork() {
        var settings=new PumpSwapCaptureSettings();var collector=mock(PumpSwapPoolCollector.class);var json=mock(tools.jackson.databind.ObjectMapper.class);
        settings.setContinuousConfigurationFile("absent-continuous.json");settings.setAuthorityAmendmentFile("absent-amendment.json");
        new PumpSwapCaptureLauncher(settings,collector,json).run(new DefaultApplicationArguments());verifyNoInteractions(collector,json);
    }
    @Test @SuppressWarnings("unchecked") void enabledLauncherCommitsAmendmentBeforeAnyContinuousStartAndSecretBlocksAllWork() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var settings=new PumpSwapCaptureSettings();settings.setEnabled(true);settings.setContinuousEnabled(true);settings.setSecret("synthetic-key-never-persisted");
        var d=PumpSwapContinuousCaptureIT.deployment("launcher-order",1,1);var request=amendment("launcher-period");
        var configurationFile=directory.resolve("continuous.json");java.nio.file.Files.writeString(configurationFile,json.writeValueAsString(d));settings.setContinuousConfigurationFile(configurationFile.toString());
        var amendmentFile=directory.resolve("amendment.json");java.nio.file.Files.writeString(amendmentFile,json.writeValueAsString(request));settings.setAuthorityAmendmentFile(amendmentFile.toString());
        var coordinator=mock(PumpSwapContinuousCaptureCoordinator.class);var service=mock(AmendCapturePeriodAuthorityUseCase.class);var collector=mock(PumpSwapPoolCollector.class);
        org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous=mock(org.springframework.beans.factory.ObjectProvider.class);
        org.springframework.beans.factory.ObjectProvider<AmendCapturePeriodAuthorityUseCase> authority=mock(org.springframework.beans.factory.ObjectProvider.class);
        when(continuous.getObject()).thenReturn(coordinator);when(authority.getObject()).thenReturn(service);
        var launcher=new PumpSwapCaptureLauncher(settings,collector,json,continuous,authority);launcher.run(new DefaultApplicationArguments());
        var order=inOrder(service,coordinator);order.verify(service).amend(request);order.verify(coordinator).start(d);verifyNoInteractions(collector);
        clearInvocations(service,coordinator,continuous,authority);
        java.nio.file.Files.writeString(amendmentFile,json.writeValueAsString(new PumpSwapCaptureAuthorityAmendment(request.version(),request.provider(),request.predecessor(),request.successor(),settings.getSecret())));
        launcher.run(new DefaultApplicationArguments());verifyNoInteractions(service,coordinator,authority);
    }
    @Test @SuppressWarnings("unchecked") void escapedSecretIsRejectedBeforeResolvingAmendmentOrStartingProviderWork() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var settings=new PumpSwapCaptureSettings();settings.setEnabled(true);settings.setContinuousEnabled(true);
        var secret="synthetic-key-never-persisted";settings.setSecret(secret);
        var d=PumpSwapContinuousCaptureIT.deployment("escaped-secret",1,1);var a=amendment("escaped-secret-period");
        var request=new PumpSwapCaptureAuthorityAmendment(a.version(),a.provider(),a.predecessor(),a.successor(),secret);
        var canonical=json.writeValueAsString(request);
        var escaped=canonical.replace(secret,"\\"+"u0073"+secret.substring(1));
        assertThat(escaped).doesNotContain(secret);assertThat(json.readValue(escaped,PumpSwapCaptureAuthorityAmendment.class).ownerDecision()).isEqualTo(secret);
        var configurationFile=directory.resolve("escaped-continuous.json");java.nio.file.Files.writeString(configurationFile,json.writeValueAsString(d));settings.setContinuousConfigurationFile(configurationFile.toString());
        var amendmentFile=directory.resolve("escaped-amendment.json");java.nio.file.Files.writeString(amendmentFile,escaped);settings.setAuthorityAmendmentFile(amendmentFile.toString());
        var coordinator=mock(PumpSwapContinuousCaptureCoordinator.class);var service=mock(AmendCapturePeriodAuthorityUseCase.class);var collector=mock(PumpSwapPoolCollector.class);
        org.springframework.beans.factory.ObjectProvider<PumpSwapContinuousCaptureCoordinator> continuous=mock(org.springframework.beans.factory.ObjectProvider.class);
        org.springframework.beans.factory.ObjectProvider<AmendCapturePeriodAuthorityUseCase> authority=mock(org.springframework.beans.factory.ObjectProvider.class);
        when(continuous.getObject()).thenReturn(coordinator);when(authority.getObject()).thenReturn(service);
        new PumpSwapCaptureLauncher(settings,collector,json,continuous,authority).run(new DefaultApplicationArguments());
        verifyNoInteractions(service,authority,coordinator,collector);
    }
    @Test void finalLogUsesActualDurableReasonAndUnknownReadIsNotSuccessfulStop() {
        var settings=new PumpSwapCaptureSettings();settings.setEnabled(true);settings.setContinuousEnabled(true);
        settings.setRpc(java.net.URI.create("http://localhost"));settings.setWebsocket(java.net.URI.create("ws://localhost"));settings.setSecret("synthetic-secret-not-in-config");
        var ledger=mock(ContinuousCaptureLedgerUseCase.class);var collector=mock(PumpSwapPoolCollector.class);var derive=mock(DeriveSealedCaptureUseCase.class);
        var d=PumpSwapContinuousCaptureIT.deployment("log-stop",1,1);
        var fence=new io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore.Fence(d.deploymentKey(),"log-holder",1);
        var a=new ContinuousCaptureLedgerUseCase.Admission(d,fence,null,false,"STALE_ADMISSION_REASON");
        when(ledger.admit(eq(d),anyString())).thenReturn(a);
        when(ledger.terminalObservation(a)).thenReturn(new ContinuousCaptureLedgerUseCase.TerminalObservation("STOPPED","DEPLOYMENT_RESOURCE_LIMIT",0,"NONE","",d.opening(),133,13));
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(PumpSwapContinuousCaptureCoordinator.class);
        var priorLevel=logger.getLevel();var priorAdditivity=logger.isAdditive();
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.setContext(logger.getLoggerContext());appender.start();
        logger.setLevel(ch.qos.logback.classic.Level.WARN);logger.setAdditive(false);logger.addAppender(appender);
        try {
            new PumpSwapContinuousCaptureCoordinator(ledger,collector,derive,settings).run(d);
            assertThat(appender.list.stream().map(e->e.getFormattedMessage()).toList()).anyMatch(s->s.contains("DEPLOYMENT_RESOURCE_LIMIT")&&s.contains("LOCAL_OPERATOR"));
            assertThat(appender.list.stream().map(e->e.getFormattedMessage()).toList()).noneMatch(s->s.contains("STALE_ADMISSION_REASON")||s.contains(settings.getSecret()));
            appender.list.clear();when(ledger.terminalObservation(a)).thenThrow(new IllegalStateException("sensitive-internal-message"));
            new PumpSwapContinuousCaptureCoordinator(ledger,collector,derive,settings).run(d);
            assertThat(appender.list.stream().map(e->e.getFormattedMessage()).toList()).anyMatch(s->s.contains("UNKNOWN")&&s.contains("OBSERVATION_UNAVAILABLE"));
            assertThat(appender.list.stream().map(e->e.getFormattedMessage()).toList()).noneMatch(s->s.contains("sensitive-internal-message"));
            verifyNoInteractions(collector,derive);
        } finally {logger.detachAppender(appender);logger.setLevel(priorLevel);logger.setAdditive(priorAdditivity);appender.stop();}
    }
}
