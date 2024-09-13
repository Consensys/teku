/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.attestation;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData;
import tech.pegasys.teku.statetransition.attestation.utils.PayloadAttestationBitsAggregator;

public class MatchingDataPayloadAttestationGroup implements Iterable<PayloadAttestation> {

  private final Spec spec;
  private final PayloadAttestationData payloadAttestationData;
  private final PayloadAttestationBitsAggregator payloadAttestationBitsAggregator;
  private final NavigableMap<Integer, Set<PayloadAttestation>> payloadAttestationsByValidatorCount =
      new TreeMap<>(Comparator.reverseOrder()); // Most validators first

  public MatchingDataPayloadAttestationGroup(
      final Spec spec, final PayloadAttestationData payloadAttestationData) {
    this.spec = spec;
    this.payloadAttestationData = payloadAttestationData;
    this.payloadAttestationBitsAggregator = createEmptyPayloadAttestationBits();
  }

  private PayloadAttestationBitsAggregator createEmptyPayloadAttestationBits() {
    return PayloadAttestationBitsAggregator.fromEmptyFromPayloadAttestationSchema(
        spec.atSlot(payloadAttestationData.getSlot())
            .getSchemaDefinitions()
            .toVersionEip7732()
            .orElseThrow()
            .getPayloadAttestationSchema());
  }

  /**
   * Adds a payload attestation message to this group. When possible, the payload attestation will
   * be aggregated with others during iteration. Ignores payload attestations with no new, unseen
   * aggregation bits.
   *
   * @param payloadAttestation the payload attestation message to add
   * @return True if the attestation was added, false otherwise
   */
  public boolean add(final PayloadAttestation payloadAttestation) {
    if (payloadAttestationBitsAggregator.isSuperSetOf(payloadAttestation)) {
      // All payload attestation bits have already been included on chain
      return false;
    }
    return payloadAttestationsByValidatorCount
        .computeIfAbsent(
            payloadAttestation.getAggregationBits().getBitCount(), count -> new HashSet<>())
        .add(payloadAttestation);
  }

  public PayloadAttestationData getPayloadAttestationData() {
    return payloadAttestationData;
  }

  @Override
  public Iterator<PayloadAttestation> iterator() {
    return new AggregatingIterator();
  }

  public int size() {
    return payloadAttestationsByValidatorCount.values().stream()
        .map(Set::size)
        .reduce(0, Integer::sum);
  }

  public Stream<PayloadAttestation> stream() {
    return StreamSupport.stream(spliterator(), false);
  }

  @SuppressWarnings("unused")
  private class AggregatingIterator implements Iterator<PayloadAttestation> {

    private final PayloadAttestationBitsAggregator payloadAttestationBitsAggregator;

    private Iterator<PayloadAttestation> remainingPayloadAttestations =
        getRemainingPayloadAttestations();

    private AggregatingIterator() {
      payloadAttestationBitsAggregator =
          MatchingDataPayloadAttestationGroup.this.payloadAttestationBitsAggregator.copy();
    }

    @Override
    public boolean hasNext() {
      if (!remainingPayloadAttestations.hasNext()) {
        remainingPayloadAttestations = getRemainingPayloadAttestations();
      }
      return remainingPayloadAttestations.hasNext();
    }

    @Override
    public PayloadAttestation next() {
      final PayloadAttestationAggregateBuilder builder =
          new PayloadAttestationAggregateBuilder(spec, payloadAttestationData);
      remainingPayloadAttestations.forEachRemaining(
          candidate -> {
            if (builder.aggregate(candidate)) {
              payloadAttestationBitsAggregator.or(candidate);
            }
          });
      return builder.buildAggregate();
    }

    public Iterator<PayloadAttestation> getRemainingPayloadAttestations() {
      return payloadAttestationsByValidatorCount.values().stream()
          .flatMap(Set::stream)
          .filter(candidate -> !payloadAttestationBitsAggregator.isSuperSetOf(candidate))
          .iterator();
    }
  }
}
