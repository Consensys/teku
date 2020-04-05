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

package tech.pegasys.artemis.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;

class AttestationPoolTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final AttestationPool pool = new AttestationPool();

  @AfterEach
  public void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  public void shouldMakeAttestationsAvailableForBlock() {
    final Attestation attestation1 = addAttestation(4);
    final Attestation attestation2 = addAttestation(5);
    final Attestation attestation3 = addAttestation(5);

    final SSZList<Attestation> result = pool.getAttestationsForBlock(UnsignedLong.valueOf(6));

    assertThat(result).containsExactlyInAnyOrder(attestation1, attestation2, attestation3);
  }

  @Test
  public void shouldNotAddAttestationsFromOrAfterBlockSlot() {
    // TODO: Hit all cases in Attestation.getEarliestSlotForProcessing
    final Attestation attestation1 = addAttestation(5);
    addAttestation(6);
    addAttestation(7);

    final SSZList<Attestation> result = pool.getAttestationsForBlock(UnsignedLong.valueOf(6));
    assertThat(result).containsExactlyInAnyOrder(attestation1);
  }

  @Test
  public void shouldNotAddMoreAttestationsThanAreAllowedInABlock() {
    Constants.MAX_ATTESTATIONS = 2;
    final Attestation attestation1 = addAttestation(1);
    final Attestation attestation2 = addAttestation(2);
    addAttestation(3);

    final SSZList<Attestation> result = pool.getAttestationsForBlock(UnsignedLong.valueOf(5));
    assertThat(result).containsExactlyInAnyOrder(attestation1, attestation2);
  }

  private Attestation addAttestation(final long slot) {
    final Attestation attestation =
        new Attestation(
            AttestationUtil.getAggregationBits(10, 0),
            dataStructureUtil.randomAttestationData(UnsignedLong.valueOf(slot)),
            BLSSignature.empty());
    pool.addAttestation(attestation);
    return attestation;
  }
}
