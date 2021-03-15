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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.teku.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.teku.util.config.Constants.MAX_ATTESTER_SLASHINGS;
import static tech.pegasys.teku.util.config.Constants.MAX_DEPOSITS;
import static tech.pegasys.teku.util.config.Constants.MAX_PROPOSER_SLASHINGS;
import static tech.pegasys.teku.util.config.Constants.MAX_VOLUNTARY_EXITS;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;

public abstract class AbstractBeaconBlockBodyTest<T extends BeaconBlockBody> {
  protected final Spec spec = SpecFactory.createMinimal();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BLSSignature blsSignature = dataStructureUtil.randomSignature();
  private final Eth1Data eth1Data = dataStructureUtil.randomEth1Data();
  private final Bytes32 graffiti = dataStructureUtil.randomBytes32();
  private final SSZMutableList<ProposerSlashing> proposerSlashings =
      SSZList.createMutable(ProposerSlashing.class, MAX_PROPOSER_SLASHINGS);
  private final SSZMutableList<AttesterSlashing> attesterSlashings =
      SSZList.createMutable(AttesterSlashing.class, MAX_ATTESTER_SLASHINGS);
  private final SSZMutableList<Attestation> attestations =
      SSZList.createMutable(Attestation.class, MAX_ATTESTATIONS);
  private final SSZMutableList<Deposit> deposits =
      SSZList.createMutable(Deposit.class, MAX_DEPOSITS);
  private final SSZMutableList<SignedVoluntaryExit> voluntaryExits =
      SSZList.createMutable(SignedVoluntaryExit.class, MAX_VOLUNTARY_EXITS);

  {
    proposerSlashings.add(dataStructureUtil.randomProposerSlashing());
    proposerSlashings.add(dataStructureUtil.randomProposerSlashing());
    proposerSlashings.add(dataStructureUtil.randomProposerSlashing());
    attesterSlashings.add(dataStructureUtil.randomAttesterSlashing());
    attestations.add(dataStructureUtil.randomAttestation());
    attestations.add(dataStructureUtil.randomAttestation());
    attestations.add(dataStructureUtil.randomAttestation());
    deposits.addAll(dataStructureUtil.randomDeposits(MAX_DEPOSITS));
    voluntaryExits.add(dataStructureUtil.randomSignedVoluntaryExit());
    voluntaryExits.add(dataStructureUtil.randomSignedVoluntaryExit());
    voluntaryExits.add(dataStructureUtil.randomSignedVoluntaryExit());
  }

  private final T defaultBlockBody = createDefaultBlockBody();

  protected abstract T createBlockBody(
      BLSSignature randaoReveal,
      Eth1Data eth1Data,
      Bytes32 graffiti,
      SSZList<ProposerSlashing> proposerSlashings,
      SSZList<AttesterSlashing> attesterSlashings,
      SSZList<Attestation> attestations,
      SSZList<Deposit> deposits,
      SSZList<SignedVoluntaryExit> voluntaryExits);

  protected abstract BeaconBlockBodySchema<T> getBlockBodySchema();

  protected T createDefaultBlockBody() {
    return createBlockBody(
        blsSignature,
        eth1Data,
        graffiti,
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits);
  }

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    T testBeaconBlockBody = defaultBlockBody;

    assertEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    T testBeaconBlockBody = createDefaultBlockBody();
    assertEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenProposerSlashingsAreDifferent() {
    // Create copy of proposerSlashings and reverse to ensure it is different.
    SSZList<ProposerSlashing> reverseProposerSlashings = proposerSlashings.reversed();

    T testBeaconBlockBody =
        createBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            SSZList.createMutable(reverseProposerSlashings),
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits);

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttesterSlashingsAreDifferent() {
    // Create copy of attesterSlashings and change the element to ensure it is different.
    SSZMutableList<AttesterSlashing> otherAttesterSlashings =
        SSZList.concat(
            SSZList.singleton(dataStructureUtil.randomAttesterSlashing()), attesterSlashings);

    T testBeaconBlockBody =
        createBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            proposerSlashings,
            otherAttesterSlashings,
            attestations,
            deposits,
            voluntaryExits);

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttestationsAreDifferent() {
    // Create copy of attestations and reverse to ensure it is different.
    SSZList<Attestation> reverseAttestations = attestations.reversed();

    T testBeaconBlockBody =
        createBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            proposerSlashings,
            attesterSlashings,
            reverseAttestations,
            deposits,
            voluntaryExits);

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenDepositsAreDifferent() {
    // Create copy of deposits and reverse to ensure it is different.
    SSZList<Deposit> reverseDeposits = deposits.reversed();

    T testBeaconBlockBody =
        createBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            proposerSlashings,
            attesterSlashings,
            attestations,
            reverseDeposits,
            voluntaryExits);

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenExitsAreDifferent() {
    // Create copy of exits and reverse to ensure it is different.
    SSZList<SignedVoluntaryExit> reverseVoluntaryExits = voluntaryExits.reversed();

    T testBeaconBlockBody =
        createBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            proposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            reverseVoluntaryExits);

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void roundTripsViaSsz() {
    BeaconBlockBody newBeaconBlockBody =
        getBlockBodySchema().sszDeserialize(defaultBlockBody.sszSerialize());
    assertEquals(defaultBlockBody, newBeaconBlockBody);
  }
}
