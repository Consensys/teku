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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;

public class CapturingIndexedAttestationProvider implements IndexedAttestationProvider {
  private final Map<Attestation, IndexedAttestation> indexedAttestations = new HashMap<>();

  @Override
  public IndexedAttestation getIndexedAttestation(
      final BeaconState state, final Attestation attestation) {
    return indexedAttestations.computeIfAbsent(
        attestation, key -> DIRECT_PROVIDER.getIndexedAttestation(state, attestation));
  }

  public Collection<IndexedAttestation> getIndexedAttestations() {
    return indexedAttestations.values();
  }
}
