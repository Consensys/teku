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

package tech.pegasys.teku.core.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.AttestationUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class CapturingIndexedAttestationProviderTest {

  private final ChainBuilder chainBuilder = ChainBuilder.createDefault();

  @BeforeEach
  void setUp() {
    chainBuilder.generateGenesis();
  }

  @Test
  void shouldStoreCreatedIndexedAttestations() {
    final CapturingIndexedAttestationProvider provider = new CapturingIndexedAttestationProvider();
    final BeaconState state = chainBuilder.getStateAtSlot(0);
    final List<Attestation> attestations =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(UInt64.ONE)
            .limit(5)
            .collect(Collectors.toList());

    final Set<IndexedAttestation> expectedIndexedAttestations =
        attestations.stream()
            .map(attestation -> AttestationUtil.get_indexed_attestation(state, attestation))
            .collect(Collectors.toSet());

    // Should get the attestations we expect
    final Set<IndexedAttestation> calculatedIndexedAttestations =
        attestations.stream()
            .map(attestation -> provider.getIndexedAttestation(state, attestation))
            .collect(Collectors.toSet());
    assertThat(calculatedIndexedAttestations)
        .containsExactlyInAnyOrderElementsOf(expectedIndexedAttestations);

    // And they should have all be captured.
    assertThat(provider.getIndexedAttestations())
        .containsExactlyInAnyOrderElementsOf(expectedIndexedAttestations);
  }
}
