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

package tech.pegasys.artemis.networking.p2p.libp2p.gossip;

import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.is_valid_indexed_attestation;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class AttestationTopicHandler extends GossipTopicHandler<Attestation> {

  private static final Logger LOG = LogManager.getLogger();
  private final Topic attestationsTopic;
  private final ChainStorageClient chainStorageClient;

  private final int committeeIndex;

  protected AttestationTopicHandler(
      final PubsubPublisherApi publisher,
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient,
      final int committeeIndex) {
    super(publisher, eventBus);
    this.committeeIndex = committeeIndex;
    this.attestationsTopic = new Topic("/eth2/index" + committeeIndex + "_beacon_attestation/ssz");
    this.chainStorageClient = chainStorageClient;
  }

  @Override
  public Topic getTopic() {
    return attestationsTopic;
  }

  @Subscribe
  public void onNewAttestation(final Attestation attestation) {
    if (toIntExact(attestation.getData().getIndex().longValue()) == committeeIndex) {
      gossip(attestation);
    }
  }

  @Override
  protected Attestation deserialize(final Bytes bytes) throws SSZException {
    return SimpleOffsetSerializer.deserialize(bytes, Attestation.class);
  }

  @Override
  protected boolean validateData(final Attestation attestation) {
    final BeaconState state =
        chainStorageClient.getStore().getBlockState(attestation.getData().getBeacon_block_root());
    if (state == null) {
      LOG.trace(
          "Attestation BeaconState was not found in Store. Attestation: ({}), block_root: ({}) on {}",
          attestation.hash_tree_root(),
          attestation.getData().getBeacon_block_root(),
          getTopic());
      return false;
    }
    final IndexedAttestation indexedAttestation = get_indexed_attestation(state, attestation);
    final boolean validAttestation = is_valid_indexed_attestation(state, indexedAttestation);
    if (!validAttestation) {
      LOG.trace(
          "Received invalid attestation ({}) on {}", attestation.hash_tree_root(), getTopic());
      return false;
    }

    return true;
  }

  public int getCommitteeIndex() {
    return committeeIndex;
  }
}
