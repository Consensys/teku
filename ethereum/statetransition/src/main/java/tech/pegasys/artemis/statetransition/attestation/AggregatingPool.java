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

import static com.google.common.base.Preconditions.checkState;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;

public class AggregatingPool {

  private final Map<Bytes, AttestationGroup> attestationGroupByDataHash = new LinkedHashMap<>();

  public synchronized void add(final Attestation attestation) {
    attestationGroupByDataHash
        .computeIfAbsent(attestation.getData().hash_tree_root(), AttestationGroup::new)
        .add(attestation);
  }

  public synchronized Optional<Attestation> removeNextBestAttestation() {
    if (attestationGroupByDataHash.isEmpty()) {
      return Optional.empty();
    }
    final Iterator<AttestationGroup> groupIterator = attestationGroupByDataHash.values().iterator();
    final AttestationGroup group = groupIterator.next();
    final Attestation attestation = group.createAggregate();
    if (group.isEmpty()) {
      groupIterator.remove();
    }
    return Optional.of(attestation);
  }

  private static class AttestationGroup {
    private final NavigableMap<Integer, Set<Attestation>> attestationsByValidatorCount =
        new TreeMap<>(Comparator.reverseOrder()); // Most validators first

    private AttestationGroup(final Bytes dataHashTreeRoot) {}

    public void add(final Attestation attestation) {
      attestationsByValidatorCount
          .computeIfAbsent(
              attestation.getAggregation_bits().countSetBits(), count -> new LinkedHashSet<>())
          .add(attestation);
    }

    public boolean isEmpty() {
      return attestationsByValidatorCount.isEmpty();
    }

    public Attestation createAggregate() {
      checkState(!attestationsByValidatorCount.isEmpty(), "No attestations to aggregate");
      final Iterator<Set<Attestation>> groupIterator =
          attestationsByValidatorCount.values().iterator();
      Attestation aggregate = null;
      while (groupIterator.hasNext()) {
        final Set<Attestation> attestations = groupIterator.next();
        checkState(!attestations.isEmpty(), "Empty attestations list encountered");
        final Iterator<Attestation> attestationIterator = attestations.iterator();
        if (aggregate == null) {
          aggregate = attestationIterator.next();
          attestationIterator.remove();
        }
        aggregate = aggregateAndRemove(aggregate, attestationIterator);
        if (attestations.isEmpty()) {
          groupIterator.remove();
        }
      }
      return aggregate;
    }

    private Attestation aggregateAndRemove(
        final Attestation initial, final Iterator<Attestation> candidateIterator) {
      Attestation current = initial;
      while (candidateIterator.hasNext()) {
        final Attestation candidate = candidateIterator.next();
        if (canAggregate(current.getAggregation_bits(), candidate.getAggregation_bits())) {
          current = AttestationUtil.aggregateAttestations(current, candidate);
          candidateIterator.remove();
        } // Otherwise skip this one and see if there are other attestations we can aggregate
      }
      return current;
    }

    private boolean canAggregate(final Bitlist current, final Bitlist other) {
      if (current.getCurrentSize() != other.getCurrentSize()) {
        return false;
      }
      return !current.intersects(other);
    }
  }
}
