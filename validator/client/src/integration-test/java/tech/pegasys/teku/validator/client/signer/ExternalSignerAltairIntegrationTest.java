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

package tech.pegasys.teku.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.createForkInfo;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.validateMetrics;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.verifySignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.loader.HttpClientExternalSignerFactory;

@ExtendWith(MockServerExtension.class)
public class ExternalSignerAltairIntegrationTest {
  private static final Duration TIMEOUT = Duration.ofMillis(500);
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final SyncCommitteeUtil syncCommitteeUtil =
      spec.getSyncCommitteeUtilRequired(UInt64.ZERO);
  private final UInt64 slot = UInt64.ZERO;
  private final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
  private final SyncAggregatorSelectionData selectionData =
      syncCommitteeUtil.createSyncAggregatorSelectionData(slot, UInt64.ZERO);
  private final BLSSignature aggregatorSignature =
      BLSSignature.fromBytesCompressed(
          Bytes.fromHexString(
              "0x8f5c34de9e22ceaa7e8d165fc0553b32f02188539e89e2cc91e2eb9077645986550d872ee3403204ae5d554eae3cac12124e18d2324bccc814775316aaef352abc0450812b3ca9fde96ecafa911b3b8bfddca8db4027f08e29c22a9c370ad933"));
  final SyncCommitteeContribution contribution =
      dataStructureUtil.randomSyncCommitteeContribution(slot, beaconBlockRoot);
  final ContributionAndProof contributionAndProof =
      syncCommitteeUtil.createContributionAndProof(
          UInt64.valueOf(11), contribution, aggregatorSignature);

  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private static final BLSKeyPair KEYPAIR = BLSTestUtil.randomKeyPair(1234);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final ThrottlingTaskQueueWithPriority queue =
      ThrottlingTaskQueueWithPriority.create(
          8, metricsSystem, TekuMetricCategory.VALIDATOR, "externalSignerTest");

  private ClientAndServer client;
  private ExternalSigner externalSigner;

  @BeforeEach
  void setup(final ClientAndServer client) throws MalformedURLException {
    this.client = client;
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerPublicKeySources(List.of(KEYPAIR.getPublicKey().toString()))
            .validatorExternalSignerUrl(new URL("http://127.0.0.1:" + client.getLocalPort()))
            .validatorExternalSignerTimeout(TIMEOUT)
            .build();
    final HttpClientExternalSignerFactory httpClientExternalSignerFactory =
        new HttpClientExternalSignerFactory(config);

    externalSigner =
        new ExternalSigner(
            spec,
            httpClientExternalSignerFactory.get(),
            config.getValidatorExternalSignerUrl(),
            KEYPAIR.getPublicKey(),
            TIMEOUT,
            queue,
            metricsSystem);
  }

  @AfterEach
  void tearDown() {
    client.reset();
  }

  @Test
  void shouldSignAltairBlock() throws Exception {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signBlock(block, fork).join();
    assertThat(response).isEqualTo(expectedSignature);

    final ExternalSignerBlockRequestProvider externalSignerBlockRequestProvider =
        new ExternalSignerBlockRequestProvider(spec, block);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignBlock(block, fork),
            externalSignerBlockRequestProvider.getSignType(),
            externalSignerBlockRequestProvider.getBlockMetadata(
                Map.of("fork_info", createForkInfo(fork))));

    verifySignRequest(client, KEYPAIR.getPublicKey().toString(), signingRequestBody);

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignSyncCommitteeMessage() throws Exception {
    final Bytes expectedSigningRoot =
        signingRootFromSyncCommitteeUtils(
                slot,
                utils ->
                    utils.getSyncCommitteeMessageSigningRoot(
                        beaconBlockRoot, spec.computeEpochAtSlot(slot), forkInfo))
            .get();
    final BLSSignature expectedSignature = BLS.sign(KEYPAIR.getSecretKey(), expectedSigningRoot);
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signSyncCommitteeMessage(slot, beaconBlockRoot, forkInfo).join();

    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            expectedSigningRoot,
            SignType.SYNC_COMMITTEE_MESSAGE,
            Map.of(
                "fork_info",
                createForkInfo(forkInfo),
                "sync_committee_message",
                Map.of("beacon_block_root", beaconBlockRoot, "slot", slot)));

    verifySignRequest(client, KEYPAIR.getPublicKey().toString(), signingRequestBody);

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignSyncCommitteeSelectionProof() throws Exception {
    final Bytes expectedSigningRoot =
        signingRootFromSyncCommitteeUtils(
                slot,
                utils -> utils.getSyncAggregatorSelectionDataSigningRoot(selectionData, forkInfo))
            .get();
    final BLSSignature expectedSignature = BLS.sign(KEYPAIR.getSecretKey(), expectedSigningRoot);
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signSyncCommitteeSelectionProof(selectionData, forkInfo).join();

    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            expectedSigningRoot,
            SignType.SYNC_COMMITTEE_SELECTION_PROOF,
            Map.of(
                "fork_info",
                createForkInfo(forkInfo),
                "sync_aggregator_selection_data",
                Map.of(
                    "slot",
                    selectionData.getSlot(),
                    "subcommittee_index",
                    selectionData.getSubcommitteeIndex())));

    verifySignRequest(client, KEYPAIR.getPublicKey().toString(), signingRequestBody);

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignContributionAndProof() throws Exception {
    final Bytes expectedSigningRoot =
        signingRootFromSyncCommitteeUtils(
                slot,
                utils -> utils.getContributionAndProofSigningRoot(contributionAndProof, forkInfo))
            .get();
    final BLSSignature expectedSignature = BLS.sign(KEYPAIR.getSecretKey(), expectedSigningRoot);
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signContributionAndProof(contributionAndProof, forkInfo).join();

    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            expectedSigningRoot,
            SignType.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF,
            Map.of(
                "fork_info",
                createForkInfo(forkInfo),
                "contribution_and_proof",
                new tech.pegasys.teku.api.schema.altair.ContributionAndProof(
                    contributionAndProof)));

    verifySignRequest(client, KEYPAIR.getPublicKey().toString(), signingRequestBody);

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  private SafeFuture<Bytes> signingRootFromSyncCommitteeUtils(
      final UInt64 slot, final Function<SyncCommitteeUtil, Bytes> createSigningRoot) {
    return SafeFuture.of(() -> createSigningRoot.apply(spec.getSyncCommitteeUtilRequired(slot)));
  }
}
