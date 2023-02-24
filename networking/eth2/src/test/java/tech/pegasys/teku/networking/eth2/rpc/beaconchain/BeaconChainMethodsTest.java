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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcRequestDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BeaconChainMethodsTest {

  private static final Bytes SSZ_RECORDED_STATUS_REQUEST_BYTES =
      Bytes.fromHexString(
          "0x54ff060000734e61507059002d00007e8c1ea2540000aa01007c30a903798306695d21d1faa76363a0070677130835e503760b0e84479b7819e6114b");
  private static final StatusMessage RECORDED_STATUS_MESSAGE_DATA =
      new StatusMessage(
          new Bytes4(Bytes.of(0, 0, 0, 0)),
          Bytes32.ZERO,
          UInt64.ZERO,
          Bytes32.fromHexString(
              "0x30A903798306695D21D1FAA76363A0070677130835E503760B0E84479B7819E6"),
          UInt64.ZERO);

  private final PeerLookup peerLookup = mock(PeerLookup.class);
  final AsyncRunner asyncRunner = new StubAsyncRunner();
  final CombinedChainDataClient combinedChainDataClient = mock(CombinedChainDataClient.class);
  final RecentChainData recentChainData = mock(RecentChainData.class);
  final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  final StatusMessageFactory statusMessageFactory = new StatusMessageFactory(recentChainData);
  final MetadataMessagesFactory metadataMessagesFactory = new MetadataMessagesFactory();

  @Test
  void testStatusRoundtripSerialization() throws Exception {
    final BeaconChainMethods methods = getMethods();
    final StatusMessage expected =
        new StatusMessage(
            Bytes4.rightPad(Bytes.of(4)),
            Bytes32.random(),
            UInt64.ZERO,
            Bytes32.random(),
            UInt64.ZERO);

    final Bytes encoded = methods.status().encodeRequest(expected);
    final RpcRequestDecoder<StatusMessage> decoder = methods.status().createRequestDecoder();
    Optional<StatusMessage> decodedRequest = decoder.decodeRequest(Utils.toByteBuf(encoded));

    assertThat(decodedRequest).contains(expected);
  }

  @Test
  public void shouldDecodeStatusMessageRequest() throws Exception {
    final BeaconChainMethods methods = getMethods();
    final RpcRequestDecoder<StatusMessage> decoder = methods.status().createRequestDecoder();
    final Optional<StatusMessage> decodedRequest =
        decoder.decodeRequest(Utils.toByteBuf(SSZ_RECORDED_STATUS_REQUEST_BYTES));
    assertThat(decodedRequest).contains(RECORDED_STATUS_MESSAGE_DATA);
  }

  @Test
  public void shouldCreateVersionedBlocksByRangeMethodWithAltairEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalAltair());

    assertThat(methods.beaconBlocksByRange().getIds())
        .containsExactly("/eth2/beacon_chain/req/beacon_blocks_by_range/2/ssz_snappy");
  }

  @Test
  public void shouldCreateUnversionedBlocksByRangeMethodWithAltairDisabled() {
    final BeaconChainMethods methods = getMethods();

    assertThat(methods.beaconBlocksByRange().getIds())
        .containsExactly("/eth2/beacon_chain/req/beacon_blocks_by_range/2/ssz_snappy");
  }

  @Test
  public void shouldCreateVersionedBlocksByRootMethodWithAltairEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalAltair());

    assertThat(methods.beaconBlocksByRoot().getIds())
        .containsExactly("/eth2/beacon_chain/req/beacon_blocks_by_root/2/ssz_snappy");
  }

  @Test
  public void shouldNotCreateBlobSidecarsByRootWithDenebDisabled() {
    final BeaconChainMethods methods = getMethods();

    assertThat(methods.blobSidecarsByRoot()).isEmpty();
  }

  @Test
  public void shouldCreateBlobSidecarsByRootWithDenebEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalDeneb());

    assertThat(methods.blobSidecarsByRoot())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly(
                        "/eth2/beacon_chain/req/blob_sidecars_by_root/1/ssz_snappy"));
  }

  @Test
  public void shouldNotCreateBlobsSidecarsByRangeWithDenebDisabled() {
    final BeaconChainMethods methods = getMethods();

    assertThat(methods.blobsSidecarsByRange()).isEmpty();
  }

  @Test
  public void shouldNotCreateBeaconBlockAndBlobsSidecarByRootWithDenebDisabled() {
    final BeaconChainMethods methods = getMethods();

    assertThat(methods.beaconBlockAndBlobsSidecarByRoot()).isEmpty();
  }

  @Test
  public void shouldCreateBlobsSidecarsByRangeWithDenebEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalDeneb());

    assertThat(methods.blobsSidecarsByRange())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly(
                        "/eth2/beacon_chain/req/blobs_sidecars_by_range/1/ssz_snappy"));
  }

  @Test
  public void shouldCreateBeaconBlockAndBlobsSidecarsByRootWithDenebEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalDeneb());

    assertThat(methods.beaconBlockAndBlobsSidecarByRoot())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly(
                        "/eth2/beacon_chain/req/beacon_block_and_blobs_sidecar_by_root/1/ssz_snappy"));
  }

  private BeaconChainMethods getMethods() {
    return getMethods(TestSpecFactory.createMinimalPhase0());
  }

  private BeaconChainMethods getMethods(final Spec spec) {
    return BeaconChainMethods.create(
        spec,
        asyncRunner,
        peerLookup,
        combinedChainDataClient,
        recentChainData,
        metricsSystem,
        statusMessageFactory,
        metadataMessagesFactory,
        RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE));
  }
}
