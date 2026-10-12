package io.cryptoresearch.marketdata.infrastructure;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime credentials/endpoints never enter durable capture configuration. */
@Component
@ConfigurationProperties("crypto.research.marketdata.capture")
public class PumpSwapCaptureSettings {
	private boolean enabled;
	private String configurationFile;
	private boolean continuousEnabled;
	private String continuousConfigurationFile;
	private String authorityAmendmentFile;
	public String getAuthorityAmendmentFile() {return authorityAmendmentFile;}
	public void setAuthorityAmendmentFile(String file) {authorityAmendmentFile=file;}
	public boolean isContinuousEnabled() { return continuousEnabled; }
	public void setContinuousEnabled(boolean enabled) { continuousEnabled=enabled; }
	public String getContinuousConfigurationFile() { return continuousConfigurationFile; }
	public void setContinuousConfigurationFile(String file) { continuousConfigurationFile=file; }
	private URI rpc;
	private URI websocket;
	private String secret;
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled=enabled; }
	public String getConfigurationFile() { return configurationFile; }
	public void setConfigurationFile(String file) { configurationFile=file; }
	public URI getRpc() { return rpc; }
	public void setRpc(URI rpc) { this.rpc=rpc; }
	public URI getWebsocket() { return websocket; }
	public void setWebsocket(URI websocket) { this.websocket=websocket; }
	public String getSecret() { return secret; }
	public void setSecret(String secret) { this.secret=secret; }
}
