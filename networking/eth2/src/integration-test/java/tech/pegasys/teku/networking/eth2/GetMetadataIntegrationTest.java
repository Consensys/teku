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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair.MetadataMessageAltair;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.phase0.MetadataMessagePhase0;

public class GetMetadataIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("generateSpec")
  public void requestMetadata_shouldSendLatestAttnets(final SpecMilestone baseMilestone)
      throws Exception {
    setUp(baseMilestone, Optional.empty());
    final PeerAndNetwork peerAndNetwork = createRemotePeerAndNetwork();
    final Eth2Peer peer = peerAndNetwork.getPeer();
    MetadataMessage md1 = peer.requestMetadata().get(10, TimeUnit.SECONDS);
    MetadataMessage md2 = peer.requestMetadata().get(10, TimeUnit.SECONDS);

    assertThat(md1.getSeqNumber()).isEqualTo(md2.getSeqNumber());
    assertThat(md1.getAttnets().getBitCount()).isEqualTo(0);

    peerAndNetwork.getNetwork().setLongTermAttestationSubnetSubscriptions(List.of(0, 1, 8));
    MetadataMessage md3 = peer.requestMetadata().get(10, TimeUnit.SECONDS);
    assertThat(md3.getSeqNumber()).isGreaterThan(md2.getSeqNumber());
    assertThat(md3.getAttnets().getBitCount()).isEqualTo(3);
    assertThat(md3.getAttnets().getBit(0)).isTrue();
    assertThat(md3.getAttnets().getBit(1)).isTrue();
    assertThat(md3.getAttnets().getBit(8)).isTrue();
  }

  @ParameterizedTest(name = "{0}->{1}")
  @MethodSource("generateSpecTransition")
  public void requestMetadata_shouldSendLatestSyncnets(
      final SpecMilestone baseMilestone, final SpecMilestone nextMilestone) throws Exception {
    setUp(baseMilestone, Optional.of(nextMilestone));
    final PeerAndNetwork peerAndNetwork = createRemotePeerAndNetwork(true, true);
    final Eth2Peer peer = peerAndNetwork.getPeer();
    MetadataMessage md1 = peer.requestMetadata().get(10, TimeUnit.SECONDS);
    MetadataMessage md2 = peer.requestMetadata().get(10, TimeUnit.SECONDS);

    assertThat(md1.getSeqNumber()).isEqualTo(md2.getSeqNumber());
    assertThat(md1.getAttnets().getBitCount()).isEqualTo(0);

    // Subscribe to some sync committee subnets
    peerAndNetwork.getNetwork().subscribeToSyncCommitteeSubnetId(1);
    peerAndNetwork.getNetwork().subscribeToSyncCommitteeSubnetId(2);
    MetadataMessage md3 = peer.requestMetadata().get(10, TimeUnit.SECONDS);
    assertThat(md3).isInstanceOf(MetadataMessageAltair.class);
    final MetadataMessageAltair altairMetadata = (MetadataMessageAltair) md3;

    // Check metadata
    assertThat(altairMetadata.getSeqNumber()).isGreaterThan(md2.getSeqNumber());
    assertThat(altairMetadata.getSyncnets().getBitCount()).isEqualTo(2);
    assertThat(altairMetadata.getSyncnets().getBit(1)).isTrue();
    assertThat(altairMetadata.getSyncnets().getBit(2)).isTrue();

    // Unsubscribe from sync committee subnet
    peerAndNetwork.getNetwork().unsubscribeFromSyncCommitteeSubnetId(2);
    MetadataMessage md4 = peer.requestMetadata().get(10, TimeUnit.SECONDS);
    assertThat(md4).isInstanceOf(MetadataMessageAltair.class);
    final MetadataMessageAltair altairMetadata2 = (MetadataMessageAltair) md4;

    // Check metadata
    assertThat(altairMetadata2.getSeqNumber()).isGreaterThan(altairMetadata.getSeqNumber());
    assertThat(altairMetadata2.getSyncnets().getBitCount()).isEqualTo(1);
    assertThat(altairMetadata2.getSyncnets().getBit(1)).isTrue();
  }

  @ParameterizedTest(name = "{0}->{1}")
  @MethodSource("generateSpecTransition")
  public void requestMetadata_shouldSendLatestAttnetsAndSyncnets(
      final SpecMilestone baseMilestone, final SpecMilestone nextMilestone) throws Exception {
    setUp(baseMilestone, Optional.of(nextMilestone));
    final PeerAndNetwork peerAndNetwork = createRemotePeerAndNetwork(true, true);
    final Eth2Peer peer = peerAndNetwork.getPeer();
    MetadataMessage md1 = peer.requestMetadata().get(10, TimeUnit.SECONDS);
    MetadataMessage md2 = peer.requestMetadata().get(10, TimeUnit.SECONDS);

    assertThat(md1.getSeqNumber()).isEqualTo(md2.getSeqNumber());
    assertThat(md1.getAttnets().getBitCount()).isEqualTo(0);

    // Update attnets and syncnets
    peerAndNetwork.getNetwork().subscribeToSyncCommitteeSubnetId(1);
    peerAndNetwork.getNetwork().setLongTermAttestationSubnetSubscriptions(List.of(0, 1, 8));
    MetadataMessage md3 = peer.requestMetadata().get(10, TimeUnit.SECONDS);
    assertThat(md3).isInstanceOf(MetadataMessageAltair.class);
    final MetadataMessageAltair altairMetadata = (MetadataMessageAltair) md3;

    assertThat(altairMetadata.getSeqNumber()).isGreaterThan(md2.getSeqNumber());
    assertThat(altairMetadata.getSyncnets().getBitCount()).isEqualTo(1);
    assertThat(altairMetadata.getSyncnets().getBit(1)).isTrue();
    assertThat(altairMetadata.getAttnets().getBitCount()).isEqualTo(3);
    assertThat(altairMetadata.getAttnets().getBit(0)).isTrue();
    assertThat(altairMetadata.getAttnets().getBit(1)).isTrue();
    assertThat(altairMetadata.getAttnets().getBit(8)).isTrue();
  }

  @ParameterizedTest(name = "{0} => {1}, nextSpecEnabledLocally={2}, nextSpecEnabledRemotely={3}")
  @MethodSource("generateSpecTransitionWithCombinationParams")
  public void requestMetadata_withDisparateVersionsEnabled(
      final SpecMilestone baseMilestone,
      final SpecMilestone nextMilestone,
      final boolean nextSpecEnabledLocally,
      final boolean nextSpecEnabledRemotely) {
    setUp(baseMilestone, Optional.of(nextMilestone));
    final Eth2Peer peer = createPeer(nextSpecEnabledLocally, nextSpecEnabledRemotely);
    final Class<?> expectedType =
        nextSpecEnabledLocally && nextSpecEnabledRemotely
            ? milestoneToMetadataClass(nextMilestone)
            : milestoneToMetadataClass(baseMilestone);

    final SafeFuture<MetadataMessage> res = peer.requestMetadata();
    waitFor(() -> assertThat(res).isDone());

    assertThat(res).isCompleted();
    final MetadataMessage metadata = res.join();
    assertThat(metadata).isInstanceOf(expectedType);
    assertThat(metadata.getSeqNumber()).isEqualTo(UInt64.ZERO);
  }

  private static Class<?> milestoneToMetadataClass(final SpecMilestone milestone) {
    switch (milestone) {
      case PHASE0:
        return MetadataMessagePhase0.class;
      case ALTAIR:
      case BELLATRIX:
      case CAPELLA:
      case DENEB:
        return MetadataMessageAltair.class;
      default:
        throw new UnsupportedOperationException("unsupported milestone: " + milestone);
    }
  }
}
