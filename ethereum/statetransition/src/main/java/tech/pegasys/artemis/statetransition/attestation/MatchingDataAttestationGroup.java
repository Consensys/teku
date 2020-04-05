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

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;

class MatchingDataAttestationGroup implements Iterable<Attestation> {

  private final NavigableMap<Integer, Set<Attestation>> attestationsByValidatorCount =
      new TreeMap<>(Comparator.reverseOrder()); // Most validators first
  private final AttestationData attestationData;

  public MatchingDataAttestationGroup(final AttestationData attestationData) {
    this.attestationData = attestationData;
  }

  public AttestationData getAttestationData() {
    return attestationData;
  }

  public void add(final Attestation attestation) {
    attestationsByValidatorCount
        .computeIfAbsent(
            attestation.getAggregation_bits().countSetBits(), count -> new LinkedHashSet<>())
        .add(attestation);
  }

  @Override
  public Iterator<Attestation> iterator() {
    return new AggregatingIterator();
  }

  public Stream<Attestation> stream() {
    return StreamSupport.stream(spliterator(), false);
  }

  public boolean isEmpty() {
    return attestationsByValidatorCount.isEmpty();
  }

  public void remove(final Attestation attestation) {
    final int validatorCount = attestation.getAggregation_bits().countSetBits();
    final Set<Attestation> attestations = attestationsByValidatorCount.get(validatorCount);
    if (attestations == null) {
      return;
    }
    attestations.remove(attestation);
    if (attestations.isEmpty()) {
      attestationsByValidatorCount.remove(validatorCount);
    }
  }

  private class AggregatingIterator implements Iterator<Attestation> {
    private final Set<Attestation> includedAttestations = new HashSet<>();

    @Override
    public boolean hasNext() {
      return streamRemainingAttestations().findAny().isPresent();
    }

    @Override
    public Attestation next() {
      final AggregateAttestationBuilder builder = new AggregateAttestationBuilder(attestationData);
      streamRemainingAttestations().filter(builder::canAggregate).forEach(builder::aggregate);
      includedAttestations.addAll(builder.getIncludedAttestations());
      return builder.buildAggregate();
    }

    public Stream<Attestation> streamRemainingAttestations() {
      return attestationsByValidatorCount.values().stream()
          .flatMap(Set::stream)
          .filter(candidate -> !includedAttestations.contains(candidate));
    }
  }
}
