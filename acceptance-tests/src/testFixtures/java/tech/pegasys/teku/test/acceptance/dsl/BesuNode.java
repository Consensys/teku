/*
 * Copyright 2019 ConsenSys AG.
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;

public class BesuNode extends Node {
  private static final Logger LOG = LogManager.getLogger();
  private static final int JSON_RPC_PORT = 8545;

  public BesuNode(final Network network) {
    super(network, "hyperledger/besu:1.5.5", LOG);
    container
        .withExposedPorts(JSON_RPC_PORT)
        .withLogConsumer(frame -> LOG.debug(frame.getUtf8String().trim()))
        .waitingFor(new HttpWaitStrategy().forPort(JSON_RPC_PORT).forPath("/liveness"))
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("besu/depositContractGenesis.json"), "/genesis.json")
        .withCommand(
            "--rpc-http-enabled",
            "--rpc-http-port",
            Integer.toString(JSON_RPC_PORT),
            "--rpc-http-cors-origins=*",
            "--host-allowlist=*",
            "--miner-enabled",
            "--miner-coinbase",
            "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
            "--genesis-file",
            "/genesis.json");
  }

  public void start() {
    container.start();
  }

  public String getDepositContractAddress() {
    return "0xdddddddddddddddddddddddddddddddddddddddd";
  }

  public String getInternalJsonRpcUrl() {
    return "http://" + nodeAlias + ":" + JSON_RPC_PORT;
  }

  public String getExternalJsonRpcUrl() {
    return "http://127.0.0.1:" + container.getMappedPort(JSON_RPC_PORT);
  }

  public String getRichBenefactorKey() {
    return "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
  }
}
