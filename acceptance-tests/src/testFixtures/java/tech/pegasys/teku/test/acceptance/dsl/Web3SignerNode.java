/*
 * Copyright 2022 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.test.acceptance.dsl;

import java.net.URI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;

public class Web3SignerNode extends Node {
  private static final Logger LOG = LogManager.getLogger();
  private static final int HTTP_API_PORT = 9000;
  private final ValidatorKeysApi validatorKeysApi =
      new ValidatorKeysApi(new SimpleHttpClient(), this::getSignerUrl, this::getApiPassword);

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
            "--network=" + networkType);
  }

  public String getValidatorRestApiUrl() {
    final String url = "http://" + nodeAlias + ":" + HTTP_API_PORT;
    LOG.debug("Signer REST url: " + url);
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

  private URI getSignerUrl() {
    return URI.create("http://127.0.0.1:" + container.getMappedPort(HTTP_API_PORT));
  }
}
