package tech.pegasys.teku.core.lookup;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;

public class CachingIndexedAttestationProvider implements IndexedAttestationProvider {
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
