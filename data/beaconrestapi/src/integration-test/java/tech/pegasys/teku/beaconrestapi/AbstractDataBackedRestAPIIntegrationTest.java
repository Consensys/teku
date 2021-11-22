/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorCache;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

@SuppressWarnings("unchecked")
public abstract class AbstractDataBackedRestAPIIntegrationTest {
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(16);

  protected Spec spec;
  protected SpecConfig specConfig;

  private static final okhttp3.MediaType JSON =
      okhttp3.MediaType.parse("application/json; charset=utf-8");
  private static final BeaconRestApiConfig CONFIG =
      BeaconRestApiConfig.builder()
          .restApiPort(0)
          .restApiEnabled(true)
          .restApiDocsEnabled(true)
          .restApiHostAllowlist(List.of("127.0.0.1", "localhost"))
          .restApiCorsAllowedOrigins(new ArrayList<>())
          .beaconLivenessTrackingEnabled(true)
          .eth1DepositContractAddress(Eth1Address.ZERO)
          .build();

  protected ActiveValidatorChannel activeValidatorChannel;

  // Mocks
  protected final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);
  protected final SyncService syncService = mock(SyncService.class);
  protected final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  protected final EventChannels eventChannels = mock(EventChannels.class);
  protected final AggregatingAttestationPool attestationPool =
      mock(AggregatingAttestationPool.class);
  protected final BlockManager blockManager = mock(BlockManager.class);
  protected final AttestationManager attestationManager = mock(AttestationManager.class);
  protected final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);
  protected final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);
  protected final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);
  protected final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);

  private StorageSystem storageSystem;

  protected RecentChainData recentChainData;
  protected CombinedChainDataClient combinedChainDataClient;

  // Update utils
  private ChainBuilder chainBuilder;
  private ChainUpdater chainUpdater;

  protected final JsonProvider jsonProvider = new JsonProvider();

  protected DataProvider dataProvider;
  protected BeaconRestApi beaconRestApi;
  private BeaconChainUtil beaconChainUtil;

  protected OkHttpClient client;
  protected final ObjectMapper objectMapper = new ObjectMapper();

  protected ForkChoice forkChoice;

  private void setupStorage(
      final StateStorageMode storageMode,
      final boolean useMockForkChoice,
      final SpecMilestone specMilestone) {
    this.spec = TestSpecFactory.createMinimal(specMilestone);
    this.specConfig = spec.getGenesisSpecConfig();
    this.storageSystem =
        InMemoryStorageSystemBuilder.create().specProvider(spec).storageMode(storageMode).build();
    activeValidatorChannel = new ActiveValidatorCache(spec, 10);
    recentChainData = storageSystem.recentChainData();
    chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
    chainUpdater = new ChainUpdater(recentChainData, chainBuilder, spec);
    forkChoice =
        useMockForkChoice
            ? mock(ForkChoice.class)
            : ForkChoice.create(
                spec, new InlineEventThread(), recentChainData, mock(ForkChoiceNotifier.class));
    beaconChainUtil =
        BeaconChainUtil.create(
            spec, recentChainData, chainBuilder.getValidatorKeys(), forkChoice, true);
  }

  private void setupAndStartRestAPI(BeaconRestApiConfig config) {
    combinedChainDataClient = storageSystem.combinedChainDataClient();
    dataProvider =
        new DataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            eth2P2PNetwork,
            syncService,
            validatorApiChannel,
            attestationPool,
            blockManager,
            attestationManager,
            true,
            activeValidatorChannel,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            syncCommitteeContributionPool);
    beaconRestApi =
        new BeaconRestApi(dataProvider, config, eventChannels, SyncAsyncRunner.SYNC_RUNNER);
    beaconRestApi.start();
    client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build();
  }

  private void setupAndStartRestAPI() {
    setupAndStartRestAPI(CONFIG);
  }

  protected void startPreGenesisRestAPIWithConfig(BeaconRestApiConfig config) {
    setupStorage(StateStorageMode.ARCHIVE, false, SpecMilestone.PHASE0);
    // Start API
    setupAndStartRestAPI(config);
  }

  protected void startRestAPIAtGenesis(final SpecMilestone specMilestone) {
    startRestAPIAtGenesis(StateStorageMode.ARCHIVE, specMilestone);
  }

  protected void startRestAPIAtGenesis() {
    startRestAPIAtGenesis(StateStorageMode.ARCHIVE, SpecMilestone.PHASE0);
  }

  protected void startRestAPIAtGenesis(
      final StateStorageMode storageMode, final SpecMilestone specMilestone) {
    // Initialize genesis
    setupStorage(storageMode, false, specMilestone);
    chainUpdater.initializeGenesis();
    // Start API
    setupAndStartRestAPI();
  }

  public List<SignedBlockAndState> createBlocksAtSlots(long... slots) {
    final UInt64[] unsignedSlots =
        Arrays.stream(slots).mapToObj(UInt64::valueOf).toArray(UInt64[]::new);
    return createBlocksAtSlots(unsignedSlots);
  }

  public void setCurrentSlot(long slot) {
    chainUpdater.setCurrentSlot(UInt64.valueOf(slot));
  }

  public ArrayList<SignedBlockAndState> createBlocksAtSlots(UInt64... slots) {
    final ArrayList<SignedBlockAndState> results = new ArrayList<>();
    for (UInt64 slot : slots) {
      final SignedBlockAndState block = chainUpdater.advanceChain(slot);
      chainUpdater.updateBestBlock(block);
      results.add(block);
    }
    return results;
  }

  // by using importBlocksAtSlots instead of createBlocksAtSlots, blocks are created
  // via the blockImporter, and this will mean forkChoice has been processed.
  // this is particularly useful if testing for missing state roots (states without blocks)
  public ArrayList<BeaconBlockAndState> importBlocksAtSlots(UInt64... slots) throws Exception {
    assertThat(beaconChainUtil).isNotNull();
    final ArrayList<BeaconBlockAndState> results = new ArrayList<>();
    for (UInt64 slot : slots) {
      final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock signedBeaconBlock =
          beaconChainUtil.createAndImportBlockAtSlot(slot.longValue());
      results.add(
          new BeaconBlockAndState(
              signedBeaconBlock.getMessage(), recentChainData.getBestState().get()));
    }
    return results;
  }

  public List<SignedBeaconBlock> createBlocksAtSlotsAndMapToApiResult(long... slots) {
    final UInt64[] unsignedSlots =
        Arrays.stream(slots).mapToObj(UInt64::valueOf).toArray(UInt64[]::new);
    return createBlocksAtSlotsAndMapToApiResult(unsignedSlots);
  }

  public List<SignedBeaconBlock> createBlocksAtSlotsAndMapToApiResult(UInt64... slots) {
    return createBlocksAtSlots(slots).stream()
        .map(SignedBlockAndState::getBlock)
        .map(SignedBeaconBlock::create)
        .collect(Collectors.toList());
  }

  public SignedBlockAndState finalizeChainAtEpoch(UInt64 epoch) {
    return chainUpdater.finalizeEpoch(epoch);
  }

  protected void assertNoContent(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_NO_CONTENT);
    assertThat(response.body().string()).isEmpty();
  }

  protected void assertGone(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_GONE);
    assertThat(response.body().string()).isEmpty();
  }

  protected void assertNotFound(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  protected void assertForbidden(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_FORBIDDEN);
    assertThat(response.body().string()).contains("Host not authorized");
  }

  protected void assertBodyEquals(final Response response, final String body) throws IOException {
    assertThat(response.body().string()).isEqualTo(body);
  }

  protected Response getResponse(final String path, final String contentType) throws IOException {
    final Request request =
        new Request.Builder().url(getUrl(path)).header("Accept", contentType).build();
    return client.newCall(request).execute();
  }

  protected Response getResponse(final String path) throws IOException {
    return getResponseFromUrl(getUrl(path));
  }

  protected Response getResponseFromUrl(final String url) throws IOException {
    final Request request = new Request.Builder().url(url).build();
    return client.newCall(request).execute();
  }

  protected String getUrl(final String path) {
    return "http://localhost:" + beaconRestApi.getListenPort() + path;
  }

  protected Response getResponse(final String route, Map<String, String> getParams)
      throws IOException {
    final String params =
        getParams.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    return getResponse(route + "?" + params);
  }

  protected Response post(final String route, final String postData) throws IOException {
    final RequestBody body = RequestBody.create(JSON, postData);
    final Request request = new Request.Builder().url(getUrl() + route).post(body).build();
    return client.newCall(request).execute();
  }

  protected String mapToJson(Map<String, Object> postParams) throws JsonProcessingException {
    return objectMapper.writer().writeValueAsString(postParams);
  }

  private String getUrl() {
    return "http://localhost:" + beaconRestApi.getListenPort();
  }

  @AfterEach
  public void tearDown() {
    beaconRestApi.stop();
  }
}
