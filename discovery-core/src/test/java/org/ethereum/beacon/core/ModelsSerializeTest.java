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

package org.ethereum.beacon.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.transition.BeaconStateExImpl;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.ProposerSlashing;
import org.ethereum.beacon.core.operations.VoluntaryExit;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.operations.attestation.Crosslink;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.operations.slashing.IndexedAttestation;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.spec.SpecConstantsResolver;
import org.ethereum.beacon.core.state.BeaconStateImpl;
import org.ethereum.beacon.core.state.Fork;
import org.ethereum.beacon.core.state.PendingAttestation;
import org.ethereum.beacon.core.state.ValidatorRecord;
import org.ethereum.beacon.core.util.BeaconBlockTestUtil;
import org.ethereum.beacon.core.util.TestDataFactory;
import org.ethereum.beacon.ssz.SSZBuilder;
import org.ethereum.beacon.ssz.SSZSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class ModelsSerializeTest {
  private SSZSerializer sszSerializer;
  private SpecConstants specConstants;
  private TestDataFactory dataFactory;

  @BeforeEach
  public void setup() {
    specConstants = BeaconChainSpec.DEFAULT_CONSTANTS;
    sszSerializer =
        new SSZBuilder()
            .withExternalVarResolver(new SpecConstantsResolver(specConstants))
            .withExtraObjectCreator(SpecConstants.class, specConstants)
            .buildSerializer();
    dataFactory = new TestDataFactory(specConstants);
  }

  @Test
  public void attestationDataTest() {
    AttestationData expected = dataFactory.createAttestationData();
    BytesValue encoded = sszSerializer.encode2(expected);
    AttestationData reconstructed = sszSerializer.decode(encoded, AttestationData.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void attestationTest() {
    Attestation expected = dataFactory.createAttestation();
    BytesValue encoded = sszSerializer.encode2(expected);
    Attestation reconstructed = sszSerializer.decode(encoded, Attestation.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void depositDataTest() {
    DepositData expected = dataFactory.createDepositData();
    BytesValue encoded = sszSerializer.encode2(expected);
    DepositData reconstructed = sszSerializer.decode(encoded, DepositData.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void depositTest() {
    Deposit expected1 = dataFactory.createDeposit1();
    Deposit expected2 = dataFactory.createDeposit2();
    BytesValue encoded1 = sszSerializer.encode2(expected1);
    BytesValue encoded2 = sszSerializer.encode2(expected2);
    Deposit reconstructed1 = sszSerializer.decode(encoded1, Deposit.class);
    Deposit reconstructed2 = sszSerializer.decode(encoded2, Deposit.class);
    assertEquals(expected1, reconstructed1);
    assertEquals(expected2, reconstructed2);
  }

  @Test
  public void exitTest() {
    VoluntaryExit expected = dataFactory.createExit();
    BytesValue encoded = sszSerializer.encode2(expected);
    VoluntaryExit reconstructed = sszSerializer.decode(encoded, VoluntaryExit.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void beaconBlockHeaderTest() {
    Random random = new Random();
    BeaconBlockHeader expected = BeaconBlockTestUtil.createRandomHeader(random);
    BytesValue encoded = sszSerializer.encode2(expected);
    BeaconBlockHeader reconstructed = sszSerializer.decode(encoded, BeaconBlockHeader.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void proposerSlashingTest() {
    Random random = new Random(1);
    ProposerSlashing expected = dataFactory.createProposerSlashing(random);
    BytesValue encoded = sszSerializer.encode2(expected);
    ProposerSlashing reconstructed = sszSerializer.decode(encoded, ProposerSlashing.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void slashableAttestationTest() {
    IndexedAttestation expected = dataFactory.createSlashableAttestation();
    BytesValue encoded = sszSerializer.encode2(expected);
    IndexedAttestation reconstructed = sszSerializer.decode(encoded, IndexedAttestation.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void attesterSlashingTest() {
    AttesterSlashing expected = dataFactory.createAttesterSlashings();
    BytesValue encoded = sszSerializer.encode2(expected);
    AttesterSlashing reconstructed = sszSerializer.decode(encoded, AttesterSlashing.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void beaconBlockBodyTest() {
    BeaconBlockBody expected = dataFactory.createBeaconBlockBody();
    BytesValue encoded = sszSerializer.encode2(expected);
    BeaconBlockBody reconstructed = sszSerializer.decode(encoded, BeaconBlockBody.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void beaconBlockTest() {
    BeaconBlock expected = dataFactory.createBeaconBlock();
    BytesValue encoded = sszSerializer.encode2(expected);
    BeaconBlock reconstructed = sszSerializer.decode(encoded, BeaconBlock.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void beaconStateTest() {
    BeaconState expected = dataFactory.createBeaconState();
    long s = System.nanoTime();
    BytesValue encoded = sszSerializer.encode2(expected);
    System.out.println(
        String.format("encode(state) = %.3fs", (System.nanoTime() - s) / 1_000_000_000d));
    s = System.nanoTime();
    BeaconState reconstructed = sszSerializer.decode(encoded, BeaconStateImpl.class);
    System.out.println(
        String.format("decode(state) = %.3fs", (System.nanoTime() - s) / 1_000_000_000d));
    assertEquals(expected, reconstructed);
  }

  @Test
  public void beaconStateExTest() {
    BeaconState expected = dataFactory.createBeaconState();
    BeaconStateEx stateEx = new BeaconStateExImpl(expected);
    BytesValue encoded = sszSerializer.encode2(stateEx);
    BeaconState reconstructed = sszSerializer.decode(encoded, BeaconStateImpl.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void crosslinkTest() {
    Crosslink expected = dataFactory.createCrosslink();
    BytesValue encoded = sszSerializer.encode2(expected);
    Crosslink reconstructed = sszSerializer.decode(encoded, Crosslink.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void forkTest() {
    Fork expected = dataFactory.createFork();
    BytesValue encoded = sszSerializer.encode2(expected);
    Fork reconstructed = sszSerializer.decode(encoded, Fork.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void pendingAttestationTest() {
    PendingAttestation expected = dataFactory.createPendingAttestation();
    BytesValue encoded = sszSerializer.encode2(expected);
    PendingAttestation reconstructed = sszSerializer.decode(encoded, PendingAttestation.class);
    assertEquals(expected, reconstructed);
  }

  @Test
  public void validatorRecordTest() {
    ValidatorRecord expected = dataFactory.createValidatorRecord();
    BytesValue encoded = sszSerializer.encode2(expected);
    ValidatorRecord reconstructed = sszSerializer.decode(encoded, ValidatorRecord.class);
    assertEquals(expected, reconstructed);
  }
}
