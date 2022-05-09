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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

public class BesuNode extends Node {
  private static final Logger LOG = LogManager.getLogger();
  private static final int JSON_RPC_PORT = 8545;
  private static final int ENGINE_JSON_RPC_PORT = 8550;
  private static final String BESU_DOCKER_IMAGE_NAME = "hyperledger/besu";
  private static final String BESU_CONFIG_FILE_PATH = "/config.toml";
  private static final ObjectMapper TOML_MAPPER = new TomlMapper();

  private final Config config;

  public BesuNode(final Network network, final BesuDockerVersion version, final Config config) {
    super(network, BESU_DOCKER_IMAGE_NAME + ":" + version.getVersion(), LOG);
    this.config = config;

    container
        .withExposedPorts(JSON_RPC_PORT, ENGINE_JSON_RPC_PORT)
        .withLogConsumer(frame -> LOG.debug(frame.getUtf8String().trim()))
        .waitingFor(new HttpWaitStrategy().forPort(JSON_RPC_PORT).forPath("/liveness"))
        .withCommand("--config-file", BESU_CONFIG_FILE_PATH);
  }

  public static BesuNode create(
      final Network network,
      final BesuDockerVersion version,
      final Consumer<Config> configOptions) {

    final Config config = new Config();
    configOptions.accept(config);

    return new BesuNode(network, version, config);
  }

  public void start() throws Exception {
    LOG.debug("Start node {}", nodeAlias);

    final Map<File, String> configFiles = config.write();
    configFiles.forEach(
        (localFile, targetPath) ->
            container.withCopyFileToContainer(
                MountableFile.forHostPath(localFile.getAbsolutePath()), targetPath));

    container.withCopyFileToContainer(
        MountableFile.forClasspathResource(config.genesisFilePath), "/genesis.json");

    container.start();
  }

  public Eth1Address getDepositContractAddress() {
    return Eth1Address.fromHexString("0xdddddddddddddddddddddddddddddddddddddddd");
  }

  public String getInternalJsonRpcUrl() {
    return "http://" + nodeAlias + ":" + JSON_RPC_PORT;
  }

  public String getExternalJsonRpcUrl() {
    return "http://127.0.0.1:" + container.getMappedPort(JSON_RPC_PORT);
  }

  public String getInternalEngineJsonRpcUrl() {
    return "http://" + nodeAlias + ":" + ENGINE_JSON_RPC_PORT;
  }

  public String getRichBenefactorKey() {
    return "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
  }

  public static class Config {
    private final Map<String, Object> configMap = new HashMap<>();
    private String genesisFilePath = "besu/depositContractGenesis.json";

    public Config() {
      configMap.put("rpc-http-enabled", true);
      configMap.put("rpc-http-port", Integer.toString(JSON_RPC_PORT));
      configMap.put("rpc-http-cors-origins", new String[] {"*"});
      configMap.put("host-allowlist", new String[] {"*"});
      configMap.put("miner-enabled", true);
      configMap.put("miner-coinbase", "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
      configMap.put("genesis-file", "/genesis.json");
    }

    public BesuNode.Config withGenesisFile(final String genesisFilePath) {
      this.genesisFilePath = genesisFilePath;
      return this;
    }

    public BesuNode.Config withMergeSupport(final boolean enableMergeSupport) {
      configMap.put("rpc-http-api", new String[] {"ETH,NET,WEB3,ENGINE"});
      configMap.put("engine-rpc-http-port", Integer.toString(ENGINE_JSON_RPC_PORT));
      configMap.put("engine-host-allowlist", new String[] {"*"});
      configMap.put("Xmerge-support", enableMergeSupport);
      return this;
    }

    public Map<File, String> write() throws Exception {
      final Map<File, String> configFiles = new HashMap<>();

      final File configFile = File.createTempFile("config", ".toml");
      configFile.deleteOnExit();
      writeTo(configFile);
      configFiles.put(configFile, BESU_CONFIG_FILE_PATH);

      return configFiles;
    }

    private void writeTo(final File configFile) throws Exception {
      TOML_MAPPER.writeValue(configFile, configMap);
    }
  }
}
