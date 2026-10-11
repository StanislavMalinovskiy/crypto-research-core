package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.PumpSwapCapture;
import io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture;

/** One discoverable real-clock crash/replacement regression, exclusively synthetic local infrastructure. */
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class PumpSwapContinuousCaptureWallClockIT {
	@Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
	@Autowired JdbcClient jdbc;
	@Autowired io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore store;
	@Autowired PersistPumpSwapOperationsUseCase persistence;
	@TempDir Path directory;
	@Test @Timeout(value=300,unit=TimeUnit.SECONDS)
	void abruptTestOwnedJvmReplacementKeepsOriginal180SecondWindowThenNaturallySealsAndClipsSuccessor() throws Exception {
		var measuredStart=System.nanoTime();var original=PumpSwapContinuousCaptureIT.deployment("wallclock-"+UUID.randomUUID(),2,4);
		var c=original.template();var config=directory.resolve("immutable-configuration.json");
		var firstSignature=LocalContinuousCaptureProvider.signature(600);var nextSignature=LocalContinuousCaptureProvider.signature(601);
		Process first=null,replacement=null;boolean passed=false;var firstLog=directory.resolve("first-jvm.log");var replacementLog=directory.resolve("replacement-jvm.log");
		try(var rpc=new LocalContinuousCaptureProvider(c);var firstSocket=new LocalCaptureWebSocket(2,(index,id)->List.of(LocalContinuousCaptureProvider.notification(id,firstSignature,100)),index->{})) {
			rpc.transactions.put(firstSignature,LocalContinuousCaptureProvider.transaction(firstSignature,100,false));
			rpc.transactions.put(nextSignature,LocalContinuousCaptureProvider.transaction(nextSignature,101,false));
			try {
				first=child(config,rpc.uri().toString(),firstSocket.uri().toString(),firstLog);
				await(()->{try{return Files.exists(firstLog) && Files.readString(firstLog).contains("LOCAL_CONTINUOUS_CHILD_READY");}catch(java.io.IOException missing){return false;}},30,"first child foundation ready before immutable original window is assigned");
				var at=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
				var template=new PumpSwapCapture.Configuration(c.sessionId(),c.version(),c.provider(),at,c.pools(),c.limits(),c.authority());
				var d=new PumpSwapContinuousCapture.Configuration(original.deploymentKey(),original.version(),template,original.allowances(),original.opening(),at.plusSeconds(210),2,180,4,30,4096,100);
				var staged=directory.resolve("configuration-staged.json");Files.writeString(staged,tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(d));Files.move(staged,config,StandardCopyOption.ATOMIC_MOVE);
				await(()->count("select count(*) from marketdata.capture_dispositions where status='CAPTURED'")>=1,60,"first child committed exact real capture");
				var segment=store.segment(d.deploymentKey(),1);var originalSession=segment.configuration().sessionId();
				assertThat(segment.configuration().limits().windowSeconds()).isEqualTo(180);
				var frozen=reserved(d.deploymentKey());var periodReserved=count("select reserved_hundredths from marketdata.capture_periods");
				var firstRaw=jdbc.sql("select row_to_json(r)::text from marketdata.raw_transactions r where transaction_value=:sig").param("sig",firstSignature).query(String.class).single();
				// Existing bounded restart recovery must reach only the already-retained lower boundary.
				// The future successor signature is deliberately excluded from this history fixture.
				for(var pool:c.pools())rpc.history.put(pool.address(),List.of(rpc.transactions.get(firstSignature)));
				var killedPid=first.pid();first.destroyForcibly();assertThat(first.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(first.isAlive()).isFalse();
				System.out.println("LOCAL_CONTINUOUS_ABRUPT_TEST_PID="+killedPid+" originalSession="+originalSession+" originalEnd="+CaptureLedgerUseCase.end(segment.configuration()));
				assertThat(jdbc.sql("select state||':'||reason from marketdata.capture_sessions where session_id=:id").param("id",originalSession).query(String.class).single()).startsWith("RUNNING:").doesNotContain("OWNER_STOP");
				await(()->!lease(d.deploymentKey()).isAfter(Instant.now()),40,"exact original deployment lease expired after abrupt process death");
				try(var resumedSocket=new LocalCaptureWebSocket(2,(index,id)->List.of(LocalContinuousCaptureProvider.notification(id,index==0?firstSignature:nextSignature,index==0?100:101)),index->{})) {
					replacement=child(config,rpc.uri().toString(),resumedSocket.uri().toString(),replacementLog);
					await(()->count("select generation from marketdata.capture_deployments")>=2,30,"replacement acquired new deployment fence");
					assertThat(store.segment(d.deploymentKey(),1).configuration()).isEqualTo(segment.configuration());
					assertThat(reserved(d.deploymentKey())).isEqualTo(frozen);assertThat(count("select reserved_hundredths from marketdata.capture_periods")).isEqualTo(periodReserved);
					assertThat(CaptureLedgerUseCase.end(segment.configuration())).isAfter(Instant.now());
					await(()->count("select count(*) from marketdata.capture_gaps where reason='RESTART_UNFINISHED_SESSION'")>=6,30,"explicit restart TX and STATE gaps retained");
					var remaining=Math.max(1,Duration.between(Instant.now(),d.globalEnd().plusSeconds(35)).getSeconds());
					assertThat(replacement.waitFor(remaining,TimeUnit.SECONDS)).as("finite real-clock replacement completed without owner stop").isTrue();
					assertThat(replacement.exitValue()).isZero();
				}
				var completed=store.segment(d.deploymentKey(),1);var successor=store.segment(d.deploymentKey(),2);
				assertThat(completed.configuration()).isEqualTo(segment.configuration());assertThat(completed.state()).isEqualTo("COMPLETE");assertThat(completed.reason()).isEqualTo("CAPTURE_TIME_LIMIT");
				assertThat(completed.sources()).isEqualTo(1);assertThat(completed.processed()).isEqualTo(1);
				assertThat(completed.sealedAt()).isAfterOrEqualTo(CaptureLedgerUseCase.end(segment.configuration()));
				assertThat(successor.configuration().startsAt()).isAfterOrEqualTo(completed.sealedAt());
				assertThat(successor.configuration().limits().windowSeconds()).isBetween(1L,30L);assertThat(CaptureLedgerUseCase.end(successor.configuration())).isBeforeOrEqualTo(d.globalEnd());
				assertThat(successor.state()).isEqualTo("COMPLETE");assertThat(successor.reason()).isEqualTo("CAPTURE_TIME_LIMIT");
				assertThat(count("select count(*) from marketdata.capture_segments")).isEqualTo(2);
				assertThat(count("select count(*) from marketdata.capture_gaps where reason like 'SEGMENT_BOUNDARY:%'")).isEqualTo(6);
				assertThat(count("select count(*) from marketdata.capture_gaps where kind='STATE' and status='RECOVERED_TRANSACTIONS'")).isZero();
				assertThat(jdbc.sql("select row_to_json(r)::text from marketdata.raw_transactions r where transaction_value=:sig").param("sig",firstSignature).query(String.class).single()).isEqualTo(firstRaw);
				for(var s:List.of(completed,successor)) {
					assertThat(s.processed()).isEqualTo(s.sources());assertThat(s.pages()).isEqualTo(1);
					var receipt=store.receipt(s,0);var saved=persistence.find(receipt.batch()).orElseThrow();
					assertThat(saved.result().decoderVersion()).isEqualTo("pumpswap-operations-v4");assertThat(saved.result().transactions()).hasSize(1);assertThat(saved.result().operations()).hasSize(1);
					assertThat(saved.result().transactions().getFirst().source().session()).isEqualTo(s.configuration().sessionId());
				}
				assertThat(count("select reserved_hundredths from marketdata.capture_periods")).isEqualTo(ContinuousCaptureLedgerUseCase.resources(completed.configuration()).plus(ContinuousCaptureLedgerUseCase.resources(successor.configuration())).reservedHundredths());
				assertThat(jdbc.sql("select state||':'||reason from marketdata.capture_deployments").query(String.class).single()).isIn("STOPPED:DEPLOYMENT_GLOBAL_END","STOPPED:DEPLOYMENT_SEGMENT_LIMIT");
				System.out.println("LOCAL_CONTINUOUS_WALLCLOCK_PASS elapsedMillis="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-measuredStart)+" first=180 successor="+successor.configuration().limits().windowSeconds()+" generation=2 billing=UNKNOWN coverage=UNVERIFIED");
				passed=true;
			} finally {
				for(var p:List.of(Optional.ofNullable(first),Optional.ofNullable(replacement)))if(p.isPresent() && p.get().isAlive()){p.get().destroyForcibly();p.get().waitFor(10,TimeUnit.SECONDS);}
				for(var log:List.of(firstLog,replacementLog))if(Files.exists(log)) {
					System.out.println("LOCAL_CONTINUOUS_CHILD_DIAGNOSTIC="+log.getFileName());
					for(var line:Files.readAllLines(log))if(!passed || line.startsWith("LOCAL_CONTINUOUS_"))System.out.println(line);
				}
			}
		}
	}
	Process child(Path config,String rpc,String websocket,Path log) throws Exception {
		var java=Path.of(System.getProperty("java.home"),"bin","java.exe");
		return new ProcessBuilder(java.toString(),"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),Child.class.getName(),config.toString(),postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword(),rpc,websocket)
			.redirectErrorStream(true).redirectOutput(log.toFile()).start();
	}
	long count(String sql) {return jdbc.sql(sql).query(Long.class).single();}
	String reserved(String key) {return jdbc.sql("select reserved::text from marketdata.capture_deployments where deployment_key=:key").param("key",key).query(String.class).single();}
	Instant lease(String key) {return jdbc.sql("select locked_until from marketdata.capture_deployments where deployment_key=:key").param("key",key).query(java.time.OffsetDateTime.class).single().toInstant();}
	static void await(java.util.function.BooleanSupplier ready,long seconds,String reason) throws Exception {
		var deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(!ready.getAsBoolean() && System.nanoTime()<deadline)Thread.sleep(100);assertThat(ready.getAsBoolean()).as(reason).isTrue();
	}
	public static class Child {
		public static void main(String[] args) throws Exception {
			try(var context=new org.springframework.boot.builder.SpringApplicationBuilder(io.cryptoresearch.CryptoResearchApplication.class).web(org.springframework.boot.WebApplicationType.NONE)
				.run("--spring.profiles.active=continuous-local-fixture","--spring.datasource.url="+args[1],"--spring.datasource.username="+args[2],"--spring.datasource.password="+args[3],"--crypto.research.marketdata.capture.enabled=false","--crypto.research.marketdata.capture.continuous-enabled=false")) {
				System.out.println("LOCAL_CONTINUOUS_CHILD_READY");
				var configFile=Path.of(args[0]);var deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
				while(!Files.exists(configFile) && System.nanoTime()<deadline)Thread.sleep(20);
				var config=context.getBean(tools.jackson.databind.ObjectMapper.class).readValue(Files.readString(Path.of(args[0])),PumpSwapContinuousCapture.Configuration.class);
				var settings=context.getBean(io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings.class);settings.setEnabled(true);settings.setContinuousEnabled(true);settings.setSecret("synthetic-continuous-secret");settings.setRpc(java.net.URI.create(args[4]));settings.setWebsocket(java.net.URI.create(args[5]));
				context.getBean(PumpSwapContinuousCaptureCoordinator.class).run(config);
				var jdbc=context.getBean(JdbcClient.class);var reason=jdbc.sql("select state||':'||reason from marketdata.capture_deployments where deployment_key=:key").param("key",config.deploymentKey()).query(String.class).single();
				System.out.println("LOCAL_CONTINUOUS_CHILD_TERMINAL="+reason);
				if(!reason.startsWith("STOPPED:DEPLOYMENT_"))throw new IllegalStateException("LOCAL_CONTINUOUS_UNEXPECTED_TERMINAL");
				var collector=context.getBean(PumpSwapPoolCollector.class);var latest=context.getBean(io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore.class).segment(config.deploymentKey(),2);
				var lifecycle=collector.lifecycle(latest.configuration().sessionId());
				if(!lifecycle.workersExited() || !lifecycle.terminalCommitted() || lifecycle.writerFailed())throw new IllegalStateException("LOCAL_CONTINUOUS_ACTUAL_EXIT_NOT_PROVEN");
				System.out.println("LOCAL_CONTINUOUS_CHILD_ACTUAL_EXIT=true reason="+lifecycle.reason());
			}
		}
	}
}
