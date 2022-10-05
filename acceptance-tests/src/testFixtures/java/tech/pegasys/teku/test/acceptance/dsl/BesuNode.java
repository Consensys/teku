/*
 * Copyright ConsenSys Software Inc., 2022
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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;

public class BesuNode extends Node {
  private static final Logger LOG = LogManager.getLogger();
  private static final int JSON_RPC_PORT = 8545;
  private static final int ENGINE_JSON_RPC_PORT = 8550;
  private static final int P2P_PORT = 30303;
  private static final String BESU_DOCKER_IMAGE_NAME = "hyperledger/besu";
  private static final String BESU_CONFIG_FILE_PATH = "/config.toml";
  private static final ObjectMapper TOML_MAPPER = new TomlMapper();

  private final Config config;

  public BesuNode(final Network network, final BesuDockerVersion version, final Config config) {
    super(network, BESU_DOCKER_IMAGE_NAME + ":" + version.getVersion(), LOG);
    this.config = config;

    container
        .withExposedPorts(JSON_RPC_PORT, ENGINE_JSON_RPC_PORT, P2P_PORT)
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

  public void restartWithEmptyDatabase() throws Exception {
    container.execInContainer("rm", "-rf", "/opt/besu");
    container.stop();
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

  public String getInternalEngineWebsocketsRpcUrl() {
    return "ws://" + nodeAlias + ":" + ENGINE_JSON_RPC_PORT;
  }

  private String getInternalP2pUrl(final String nodeId) {
    return "enode://" + nodeId + "@" + getInternalIpAddress() + ":" + P2P_PORT;
  }

  private String getInternalIpAddress() {
    return container.getContainerInfo().getNetworkSettings().getNetworks().values().stream()
        .findFirst()
        .get()
        .getIpAddress();
  }

  private String fetchEnodeUrl() throws Exception {
    final URI baseUri = new URI(getExternalJsonRpcUrl());
    final String response =
        httpClient.post(baseUri, "", JSON_PROVIDER.objectToJSON(new Request("admin_nodeInfo")));
    final ObjectMapper objectMapper = JSON_PROVIDER.getObjectMapper();
    final JavaType nodeInfoResponseType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(Response.class, NodeInfoResponse.class);
    final Response<NodeInfoResponse> nodeInfoResponse =
        objectMapper.readValue(response, nodeInfoResponseType);
    return getInternalP2pUrl(nodeInfoResponse.result.id);
  }

  public Boolean addPeer(final BesuNode node) throws Exception {
    final String enode = node.fetchEnodeUrl();
    final URI baseUri = new URI(getExternalJsonRpcUrl());
    final String response =
        httpClient.post(
            baseUri, "", JSON_PROVIDER.objectToJSON(new Request("admin_addPeer", enode)));
    final ObjectMapper objectMapper = JSON_PROVIDER.getObjectMapper();
    final JavaType removePeerResponseType =
        objectMapper.getTypeFactory().constructParametricType(Response.class, Boolean.class);
    final Response<Boolean> removePeerResponse =
        objectMapper.readValue(response, removePeerResponseType);
    return removePeerResponse.result;
  }

  public String getRichBenefactorKey() {
    return "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
  }

  @SuppressWarnings("unused")
  private static class Request {
    public final String jsonrpc = "2.0";
    public final String method;
    public final String[] params;
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    public final int id;

    public Request(String method, String... params) {
      this.method = method;
      this.params = params;
      this.id = ID_COUNTER.incrementAndGet();
    }
  }

  private static class Response<T> {
    public T result;
  }

  private static class NodeInfoResponse {
    public String id;
  }

  public static class Config {
    private static final String[] MERGE_RPC_MODULES = new String[] {"ETH,NET,WEB3,ENGINE,ADMIN"};
    private final Map<String, Object> configMap = new HashMap<>();
    private Optional<URL> maybeJwtFile = Optional.empty();
    private String genesisFilePath = "besu/depositContractGenesis.json";

    public Config() {
      configMap.put("rpc-http-enabled", true);
      configMap.put("rpc-ws-enabled", true);
      configMap.put("rpc-http-port", Integer.toString(JSON_RPC_PORT));
      configMap.put("rpc-http-cors-origins", new String[] {"*"});
      configMap.put("host-allowlist", new String[] {"*"});
      configMap.put("discovery-enabled", false);
      configMap.put("genesis-file", "/genesis.json");
    }

    public BesuNode.Config withMiningEnabled(final boolean enabled) {
      configMap.put("miner-enabled", enabled);
      configMap.put("miner-coinbase", "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
      return this;
    }

    public BesuNode.Config withGenesisFile(final String genesisFilePath) {
      this.genesisFilePath = genesisFilePath;
      return this;
    }

    public BesuNode.Config withMergeSupport(final boolean enableMergeSupport) {
      configMap.put("rpc-http-api", MERGE_RPC_MODULES);
      configMap.put("rpc-ws-api", MERGE_RPC_MODULES);
      configMap.put("engine-rpc-port", Integer.toString(ENGINE_JSON_RPC_PORT));
      configMap.put("engine-host-allowlist", new String[] {"*"});
      return this;
    }

    public BesuNode.Config withJwtTokenAuthorization(final URL jwtFile) {
      configMap.put("engine-jwt-enabled", Boolean.TRUE);
      configMap.put("engine-jwt-secret", JWT_SECRET_FILE_PATH);
      this.maybeJwtFile = Optional.of(jwtFile);
      return this;
    }

    public BesuNode.Config withP2pEnabled(final boolean enabled) {
      configMap.put("p2p-enabled", enabled);
      return this;
    }

    public Map<File, String> write() throws Exception {
      final Map<File, String> configFiles = new HashMap<>();

      final File configFile = File.createTempFile("config", ".toml");
      configFile.deleteOnExit();
      writeTo(configFile);
      configFiles.put(configFile, BESU_CONFIG_FILE_PATH);

      if (maybeJwtFile.isPresent()) {
        configFiles.put(copyToTmpFile(maybeJwtFile.get()), JWT_SECRET_FILE_PATH);
      }

      return configFiles;
    }

    private void writeTo(final File configFile) throws Exception {
      TOML_MAPPER.writeValue(configFile, configMap);
    }
  }
}
