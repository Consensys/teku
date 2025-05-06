/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;

public class MetadataMessagesFactory {
  private static final Logger LOG = LogManager.getLogger();

  private final AtomicLong seqNumberGenerator = new AtomicLong(0L);
  private Iterable<Integer> attestationSubnetIds = Collections.emptyList();
  private Iterable<Integer> syncCommitteeSubnetIds = Collections.emptyList();
  // TODO-fulu update with Fulu networking-related changes (CustodyGroupCountChannel)
  private final Optional<UInt64> custodyGroupCount = Optional.empty();

  public synchronized void updateAttestationSubnetIds(
      final Iterable<Integer> attestationSubnetIds) {
    this.attestationSubnetIds = attestationSubnetIds;
    handleUpdate();
  }

  public synchronized void updateSyncCommitteeSubnetIds(
      final Iterable<Integer> syncCommitteeSubnetIds) {
    this.syncCommitteeSubnetIds = syncCommitteeSubnetIds;
    handleUpdate();
  }

  private void handleUpdate() {
    seqNumberGenerator.incrementAndGet();
  }

  public synchronized MetadataMessage createMetadataMessage(final MetadataMessageSchema<?> schema) {
    final MetadataMessage metadataMessage =
        schema.create(
            getCurrentSeqNumber(), attestationSubnetIds, syncCommitteeSubnetIds, custodyGroupCount);
    LOG.debug("Created metadata message {}", metadataMessage);
    return metadataMessage;
  }

  public PingMessage createPingMessage() {
    return new PingMessage(getCurrentSeqNumber());
  }

  private UInt64 getCurrentSeqNumber() {
    return UInt64.valueOf(seqNumberGenerator.get());
  }
}
