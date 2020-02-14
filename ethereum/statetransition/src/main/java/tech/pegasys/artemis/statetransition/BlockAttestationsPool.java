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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.datastructures.util.AttestationUtil.getAttesterIndicesIntoCommittee;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.setBitsForNewAttestation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.storage.ChainStorage;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.config.Constants;

public class BlockAttestationsPool {

  // TODO: memory clean up code brought over from ChainStorageClient,
  // requires to be updated to new data structures to make it work.
  // Memory Cleaning
  //  private final ConcurrentHashMap<Bytes32, UnsignedLong> dataRootToSlot = new
  // ConcurrentHashMap<>();

  @VisibleForTesting
  final ConcurrentHashMap<Bytes32, Bitlist> unprocessedAttestationsBitlist =
      new ConcurrentHashMap<>();

  @VisibleForTesting
  final ConcurrentHashMap<Bytes32, Bitlist> processedAttestationsBitlist =
      new ConcurrentHashMap<>();

  private final int QUEUE_INITIAL_CAPACITY = 1024;

  @VisibleForTesting
  final Queue<Attestation> aggregateAttesationsQueue =
      new PriorityBlockingQueue<>(
          QUEUE_INITIAL_CAPACITY, Comparator.comparing(a -> a.getData().getSlot()));

  public SSZList<Attestation> getAttestationsForSlot(final UnsignedLong slot) {
    SSZList<Attestation> attestations = BeaconBlockBodyLists.createAttestations();
    if (slot.compareTo(
            UnsignedLong.valueOf(
                Constants.GENESIS_SLOT + Constants.MIN_ATTESTATION_INCLUSION_DELAY))
        >= 0) {

      UnsignedLong attestation_slot =
          slot.minus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY));

      attestations = getAggregatedAttestationsForBlockAtSlot(attestation_slot);
    }
    return attestations;
  }

  public void addUnprocessedAggregateAttestationToQueue(Attestation newAttestation) {

    Bytes32 attestationDataHash = newAttestation.getData().hash_tree_root();
    AtomicBoolean oldBitlistPresent = new AtomicBoolean(true);
    final Bitlist oldBitlist =
        unprocessedAttestationsBitlist.computeIfAbsent(
            attestationDataHash,
            (key) -> {
              oldBitlistPresent.set(false);
              return newAttestation.getAggregation_bits().copy();
            });

    if (oldBitlistPresent.get()) {
      if (!setBitsForNewAttestation(oldBitlist, newAttestation)) {
        return;
      }
    }

    aggregateAttesationsQueue.add(newAttestation);

    //    ChainStorage.add(attestationDataHash, newAttestation.getData().getSlot(), dataRootToSlot);
  }

  public void addAggregateAttestationProcessedInBlock(Attestation attestation) {
    Bytes32 attestationDataHash = attestation.getData().hash_tree_root();
    final Bitlist bitlist =
        processedAttestationsBitlist.computeIfAbsent(
            attestationDataHash,
            (key) -> {
              //              ChainStorage.add(
              //                  attestationDataHash, attestation.getData().getSlot(),
              // dataRootToSlot);
              return attestation.getAggregation_bits().copy();
            });

    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      if (attestation.getAggregation_bits().getBit(i) == 1) bitlist.setBit(i);
    }
  }

  private SSZList<Attestation> getAggregatedAttestationsForBlockAtSlot(UnsignedLong slot) {
    SSZList<Attestation> attestations =
        new SSZList<>(Attestation.class, Constants.MAX_ATTESTATIONS);
    int numAttestations = 0;

    while (aggregateAttesationsQueue.peek() != null
        && aggregateAttesationsQueue.peek().getData().getSlot().compareTo(slot) <= 0
        && numAttestations < Constants.MAX_ATTESTATIONS) {

      Attestation aggregate = aggregateAttesationsQueue.remove();
      Bytes32 attestationHashTreeRoot = aggregate.getData().hash_tree_root();

      // Check if attestation has already been processed in successful block
      if (ChainStorage.get(attestationHashTreeRoot, this.processedAttestationsBitlist)
          .isPresent()) {
        Bitlist bitlist =
            ChainStorage.get(
                    aggregate.getData().hash_tree_root(), this.processedAttestationsBitlist)
                .get();
        List<Integer> oldAttesters = getAttesterIndicesIntoCommittee(bitlist);
        List<Integer> newAttesters =
            getAttesterIndicesIntoCommittee(aggregate.getAggregation_bits());
        if (oldAttesters.containsAll(newAttesters)) {
          continue;
        }
      }
      attestations.add(aggregate);
      numAttestations++;
    }
    return attestations;
  }

  /* // Memory Cleaning

  public void cleanAttestationsUntilEpoch(UnsignedLong epoch) {
    Iterator<Bytes32> it = processedAttestationsBitlist.keySet().iterator();
    while (it.hasNext()) {
      Bytes32 key = it.next();
      UnsignedLong currentEpoch = dataRootToSlot.get(key);
      if (currentEpoch.compareTo(epoch) < 0) {
        it.remove();
        dataRootToSlot.remove(key);
      }
    }
  }

  @Subscribe
  public void onNewSlot(SlotEvent slotEvent) {
    if (slotEvent
            .getSlot()
            .mod(UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH))
            .compareTo(UnsignedLong.ZERO)
            == 0) {
      UnsignedLong epoch = BeaconStateUtil.compute_epoch_at_slot(slotEvent.getSlot());
      if (epoch.compareTo(UnsignedLong.ONE) > 0) {
        cleanAttestationsUntilEpoch(epoch.minus(UnsignedLong.ONE));
      }
    }
  }*/
}
