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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.statetransition.AttestationGenerator.diffSlotAttestationData;
import static tech.pegasys.artemis.statetransition.AttestationGenerator.getSingleAttesterIndex;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLS;
import tech.pegasys.artemis.bls.BLSKeyGenerator;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.validator.AggregatorInformation;
import tech.pegasys.artemis.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.artemis.storage.client.RecentChainData;

class AttestationAggregatorTest {

  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(12);
  private final RecentChainData storageClient = MemoryOnlyRecentChainData.create(new EventBus());
  private AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
  private AttestationAggregator aggregator;

  @BeforeEach
  void setup() {
    BeaconChainUtil.initializeStorage(storageClient, validatorKeys);
    aggregator = new AttestationAggregator();
  }

  @Test
  void addOwnValidatorAttestation_newData() throws Exception {
    Attestation attestation = attestationGenerator.validAttestation(storageClient);
    int validatorIndex = new Random().nextInt(1000);
    aggregator.committeeIndexToAggregatorInformation.put(
        attestation.getData().getIndex(),
        new AggregatorInformation(BLSSignature.random(42), validatorIndex));
    aggregator.addOwnValidatorAttestation(attestation);
    Attestation aggregateAttesation = aggregator.getAggregateAndProofs().get(0).getAggregate();
    assertEquals(attestation, aggregateAttesation);
  }

  @Test
  void addOwnValidatorAttestation_oldData_noNewAttester() throws Exception {
    Attestation attestation = attestationGenerator.validAttestation(storageClient);
    int validatorIndex = new Random().nextInt(1000);
    aggregator.committeeIndexToAggregatorInformation.put(
        attestation.getData().getIndex(),
        new AggregatorInformation(BLSSignature.random(42), validatorIndex));
    aggregator.addOwnValidatorAttestation(attestation);
    Attestation newAttestation = new Attestation(attestation);
    newAttestation.setAggregate_signature(BLSSignature.random(97));
    aggregator.addOwnValidatorAttestation(newAttestation);
    assertEquals(aggregator.getAggregateAndProofs().size(), 1);
    assertEquals(attestation, aggregator.getAggregateAndProofs().get(0).getAggregate());
  }

  @Test
  void addOwnValidatorAttestation_oldData_newAttester() throws Exception {
    Attestation attestation = attestationGenerator.validAttestation(storageClient);
    BLSSignature sig1 = attestation.getAggregate_signature();
    int validatorIndex = new Random().nextInt(1000);
    aggregator.committeeIndexToAggregatorInformation.put(
        attestation.getData().getIndex(),
        new AggregatorInformation(BLSSignature.random(42), validatorIndex));
    aggregator.addOwnValidatorAttestation(attestation);
    Attestation newAttestation = AttestationGenerator.withNewSingleAttesterBit(attestation);
    int newAttesterIndex = getSingleAttesterIndex(newAttestation);
    BLSSignature sig2 = BLSSignature.random(97);
    newAttestation.setAggregate_signature(sig2);
    aggregator.addOwnValidatorAttestation(newAttestation);
    assertEquals(aggregator.getAggregateAndProofs().size(), 1);
    assertTrue(
        aggregator
            .getAggregateAndProofs()
            .get(0)
            .getAggregate()
            .getAggregation_bits()
            .getBit(newAttesterIndex));
    assertEquals(
        aggregator.getAggregateAndProofs().get(0).getAggregate().getAggregate_signature(),
        BLS.aggregate(List.of(sig1, sig2)));
  }

  @Test
  void processAttestation_newData_noOwnValidatorAttestationExists() throws Exception {
    Attestation attestation = attestationGenerator.validAttestation(storageClient);
    int validatorIndex = new Random().nextInt(1000);
    aggregator.committeeIndexToAggregatorInformation.put(
        attestation.getData().getIndex(),
        new AggregatorInformation(BLSSignature.random(42), validatorIndex));
    aggregator.addOwnValidatorAttestation(attestation);
    Attestation newAttestation = new Attestation(attestation);
    newAttestation.setData(
        diffSlotAttestationData(
            attestation.getData().getSlot().plus(UnsignedLong.ONE), attestation.getData()));
    newAttestation.setAggregate_signature(BLSSignature.random(97));
    aggregator.processAttestation(newAttestation);
    assertEquals(aggregator.getAggregateAndProofs().size(), 1);
    assertEquals(attestation, aggregator.getAggregateAndProofs().get(0).getAggregate());
  }

  @Test
  void processAttestation_oldData_noNewAttester() throws Exception {
    Attestation attestation = attestationGenerator.validAttestation(storageClient);
    int validatorIndex = new Random().nextInt(1000);
    aggregator.committeeIndexToAggregatorInformation.put(
        attestation.getData().getIndex(),
        new AggregatorInformation(BLSSignature.random(42), validatorIndex));
    aggregator.addOwnValidatorAttestation(attestation);
    Attestation newAttestation = new Attestation(attestation);
    newAttestation.setAggregate_signature(BLSSignature.random(97));
    aggregator.processAttestation(newAttestation);
    assertEquals(aggregator.getAggregateAndProofs().size(), 1);
    assertEquals(attestation, aggregator.getAggregateAndProofs().get(0).getAggregate());
  }

  @Test
  void processAttestation_oldData_newAttester() throws Exception {
    Attestation attestation = attestationGenerator.validAttestation(storageClient);
    BLSSignature sig1 = attestation.getAggregate_signature();
    int validatorIndex = new Random().nextInt(1000);
    aggregator.committeeIndexToAggregatorInformation.put(
        attestation.getData().getIndex(),
        new AggregatorInformation(BLSSignature.random(42), validatorIndex));
    aggregator.addOwnValidatorAttestation(attestation);
    Attestation newAttestation = AttestationGenerator.withNewSingleAttesterBit(attestation);
    int newAttesterIndex = getSingleAttesterIndex(newAttestation);
    BLSSignature sig2 = BLSSignature.random(97);
    newAttestation.setAggregate_signature(sig2);
    aggregator.processAttestation(newAttestation);
    assertEquals(aggregator.getAggregateAndProofs().size(), 1);
    assertTrue(
        aggregator
            .getAggregateAndProofs()
            .get(0)
            .getAggregate()
            .getAggregation_bits()
            .getBit(newAttesterIndex));
    assertEquals(
        aggregator.getAggregateAndProofs().get(0).getAggregate().getAggregate_signature(),
        BLS.aggregate(List.of(sig1, sig2)));
  }

  @Test
  void reset() throws Exception {
    Attestation attestation = attestationGenerator.validAttestation(storageClient);
    int validatorIndex = new Random().nextInt(1000);
    aggregator.committeeIndexToAggregatorInformation.put(
        attestation.getData().getIndex(),
        new AggregatorInformation(BLSSignature.random(42), validatorIndex));
    aggregator.addOwnValidatorAttestation(attestation);
    Attestation newAttestation = AttestationGenerator.withNewSingleAttesterBit(attestation);
    BLSSignature sig2 = BLSSignature.random(97);
    newAttestation.setAggregate_signature(sig2);
    aggregator.processAttestation(newAttestation);
    aggregator.reset();
    assertEquals(aggregator.getAggregateAndProofs().size(), 0);
  }
}
