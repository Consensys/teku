package tech.pegasys.teku.core.lookup;

import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.AttestationUtil;

@FunctionalInterface
public interface IndexedAttestationProvider {
  IndexedAttestationProvider DIRECT_PROVIDER = AttestationUtil::get_indexed_attestation;

  IndexedAttestation getIndexedAttestation(BeaconState state, Attestation attestation);
}
