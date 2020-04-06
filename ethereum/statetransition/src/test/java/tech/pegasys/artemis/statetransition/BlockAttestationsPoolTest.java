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

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.artemis.statetransition.AttestationGenerator.diffSlotAttestationData;
import static tech.pegasys.artemis.statetransition.AttestationGenerator.withNewAttesterBits;
import static tech.pegasys.artemis.util.config.Constants.MAX_ATTESTATIONS;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;

class BlockAttestationsPoolTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final AttestationGenerator attestationGenerator = new AttestationGenerator(emptyList());
  private BlockAttestationsPool pool;

  @BeforeEach
  void setup() {
    pool = new BlockAttestationsPool();
  }

  @Test
  @SuppressWarnings("UnusedVariable")
  void unprocessedAggregate_NewData() {
    Attestation attestation = attestationGenerator.aggregateAttestation(10);
    pool.addUnprocessedAggregateAttestationToQueue(attestation);
    assertTrue(pool.aggregateAttestationsQueue.contains(attestation));
    assertTrue(
        pool.unprocessedAttestationsBitlist.containsValue(attestation.getAggregation_bits()));
  }

  @Test
  void unprocessedAggregate_OldData_DifferentBitlist_BitlistUpdated() {
    Attestation attestation = attestationGenerator.aggregateAttestation(10);
    Attestation newAttestation = withNewAttesterBits(attestation, 1);

    pool.addUnprocessedAggregateAttestationToQueue(attestation);
    pool.addUnprocessedAggregateAttestationToQueue(newAttestation);
    Bytes32 attestationDataHash = attestation.getData().hash_tree_root();
    Bitlist bitlist = pool.unprocessedAttestationsBitlist.get(attestationDataHash);
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      final boolean expected =
          attestation.getAggregation_bits().getBit(i)
              || newAttestation.getAggregation_bits().getBit(i);
      assertEquals(bitlist.getBit(i), expected);
    }
    assert (pool.aggregateAttestationsQueue.size() == 2);
  }

  @Test
  void unprocessedAggregate_OldData_SameBitlist_ShouldBeIgnored() {
    Attestation attestation = attestationGenerator.aggregateAttestation(10);
    pool.addUnprocessedAggregateAttestationToQueue(attestation);
    Attestation newAttestation = new Attestation(attestation);
    pool.addUnprocessedAggregateAttestationToQueue(newAttestation);
    assert (pool.aggregateAttestationsQueue.size() == 1);
  }

  @Test
  void processedAggregate_NewData_SetBits() throws Exception {
    Attestation attestation = attestationGenerator.aggregateAttestation(10);
    pool.addAggregateAttestationProcessedInBlock(attestation);
    Bytes32 attestationDataHash = attestation.getData().hash_tree_root();
    Bitlist bitlist = pool.processedAttestationsBitlist.get(attestationDataHash);
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      if (bitlist.getBit(i) != attestation.getAggregation_bits().getBit(i)) {
        fail();
      }
    }
  }

  @Test
  void processedAggregate_OldData_DifferentBitlist_SetBits() {
    Attestation attestation = attestationGenerator.aggregateAttestation(10);
    pool.addAggregateAttestationProcessedInBlock(attestation);
    Bytes32 attestationDataHash = attestation.getData().hash_tree_root();

    Attestation newAttestation = withNewAttesterBits(attestation, 1);
    pool.addAggregateAttestationProcessedInBlock(newAttestation);
    Bitlist bitlist = pool.processedAttestationsBitlist.get(attestationDataHash);
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      final boolean expected =
          attestation.getAggregation_bits().getBit(i)
              || newAttestation.getAggregation_bits().getBit(i);
      assertEquals(bitlist.getBit(i), expected);
    }
  }

  @Test
  void getAggregatedAttestations_DoesNotReturnAttestationWithNoNewBits() throws Exception {
    Attestation attestation = attestationGenerator.aggregateAttestation(10);
    pool.addAggregateAttestationProcessedInBlock(attestation);
    pool.addUnprocessedAggregateAttestationToQueue(attestation);

    assertEquals(pool.getAttestationsForSlot(UnsignedLong.MAX_VALUE).size(), 0);
  }

  @Test
  void getAggregatedAttestations_DoesNotReturnAttestationsMoreThanMaxAttestations() {
    for (int i = 0; i < MAX_ATTESTATIONS + 1; i++) {
      Attestation attestation = dataStructureUtil.randomAttestation();
      attestation.setData(diffSlotAttestationData(UnsignedLong.valueOf(i), attestation.getData()));
      pool.addUnprocessedAggregateAttestationToQueue(attestation);
    }

    assertEquals(pool.getAttestationsForSlot(UnsignedLong.MAX_VALUE).size(), MAX_ATTESTATIONS);
  }

  @Test
  void getAggregatedAttestations_DoesNotReturnAttestationsWithSlotsHigherThanGivenSlot() {
    int SLOT = 10;
    for (int i = 0; i < SLOT; i++) {
      Attestation attestation = dataStructureUtil.randomAttestation();
      attestation.setData(diffSlotAttestationData(UnsignedLong.valueOf(i), attestation.getData()));
      pool.addUnprocessedAggregateAttestationToQueue(attestation);
    }

    UnsignedLong CUTOFF_SLOT = UnsignedLong.valueOf(5);
    pool.getAttestationsForSlot(CUTOFF_SLOT)
        .forEach(
            attestation -> {
              assertTrue(attestation.getData().getSlot().compareTo(CUTOFF_SLOT) <= 0);
            });
  }
}
