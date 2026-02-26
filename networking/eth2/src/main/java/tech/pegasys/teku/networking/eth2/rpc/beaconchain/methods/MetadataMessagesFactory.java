/*
 * Copyright Consensys Software Inc., 2026
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;

public class MetadataMessagesFactory implements CustodyGroupCountChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Lock lock = new ReentrantLock();
  private final AtomicLong seqNumberGenerator = new AtomicLong(0L);
  private Iterable<Integer> attestationSubnetIds = Collections.emptyList();
  private Iterable<Integer> syncCommitteeSubnetIds = Collections.emptyList();
  private Optional<UInt64> custodyGroupCount = Optional.empty();

  public void updateAttestationSubnetIds(final Iterable<Integer> attestationSubnetIds) {
    lock.lock();
    try {
      this.attestationSubnetIds = attestationSubnetIds;
      handleUpdate();
    } finally {
      lock.unlock();
    }
  }

  public void updateSyncCommitteeSubnetIds(final Iterable<Integer> syncCommitteeSubnetIds) {
    lock.lock();
    try {
      this.syncCommitteeSubnetIds = syncCommitteeSubnetIds;
      handleUpdate();
    } finally {
      lock.unlock();
    }
  }

  public void updateCustodyGroupCount(final UInt64 custodyGroupCount) {
    lock.lock();
    try {
      this.custodyGroupCount = Optional.of(custodyGroupCount);
      LOG.info("Updating custody group count {}", custodyGroupCount);
      handleUpdate();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void onGroupCountUpdate(final int custodyGroupCount, final int samplingGroupCount) {
    // we don't care until it's synced
  }

  @Override
  public void onCustodyGroupCountSynced(final int groupCount) {
    updateCustodyGroupCount(UInt64.valueOf(groupCount));
  }

  private void handleUpdate() {
    seqNumberGenerator.incrementAndGet();
  }

  public MetadataMessage createMetadataMessage(final MetadataMessageSchema<?> schema) {
    lock.lock();
    try {
      final MetadataMessage metadataMessage =
          schema.create(
              getCurrentSeqNumber(),
              attestationSubnetIds,
              syncCommitteeSubnetIds,
              custodyGroupCount);
      LOG.debug("Created metadata message {}", metadataMessage);
      return metadataMessage;
    } finally {
      lock.unlock();
    }
  }

  public PingMessage createPingMessage() {
    return new PingMessage(getCurrentSeqNumber());
  }

  private UInt64 getCurrentSeqNumber() {
    return UInt64.valueOf(seqNumberGenerator.get());
  }
}
