package tech.pegasys.teku.test.acceptance.dsl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;

import java.net.URI;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class Web3SignerNode extends Node {
  private static final Logger LOG = LogManager.getLogger();
  private static final int HTTP_API_PORT = 9000;
  private final ValidatorKeysApi validatorKeysApi =
      new ValidatorKeysApi(
          new SimpleHttpClient(), this::getSignerUrl, this::getApiPassword);

  public Web3SignerNode(final Network network, final String networkType) {
    super(network, "consensys/web3signer:develop", LOG);
    container
        .withExposedPorts(HTTP_API_PORT)
        .withLogConsumer(frame -> LOG.debug(frame.getUtf8String().trim()))
        .waitingFor(new HttpWaitStrategy().forPort(HTTP_API_PORT).forPath("/upcheck"))
        .withCommand(
            "--logging=DEBUG",
            "--http-host-allowlist=*",
            "--http-listen-host=0.0.0.0",
            "eth2",
            "--slashing-protection-enabled=false",
            "--key-manager-api-enabled=true",
            "--network=" +networkType
        );
  }

  public String getValidatorRestApiUrl() {
    final String url = "http://" + nodeAlias + ":" +  HTTP_API_PORT;
    LOG.debug("Node REST url: " + url);
    return url;
  }

  public ValidatorKeysApi getValidatorKeysApi() {
    return validatorKeysApi;
  }

  private String getApiPassword() {
    return "";
  }

  public void start() {
    container.start();
  }

  public void stop() {
    container.stop();
  }

  private URI getSignerUrl() {
    return URI.create("http://127.0.0.1:" + container.getMappedPort(HTTP_API_PORT));
  }

}
