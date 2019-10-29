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

import static java.util.Collections.emptyList;

import com.google.common.base.Objects;
import java.util.List;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.ProposerSlashing;
import org.ethereum.beacon.core.operations.Transfer;
import org.ethereum.beacon.core.operations.VoluntaryExit;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.collections.ReadList;

/**
 * Beacon block body.
 *
 * <p>Contains lists of beacon chain operations.
 *
 * @see BeaconBlock
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#beaconblockbody">BeaconBlockBody
 *     in the spec</a>
 */
@SSZSerializable
public class BeaconBlockBody {

  /** RANDAO signature submitted by proposer. */
  @SSZ private final BLSSignature randaoReveal;
  /** Eth1 data that is observed by proposer. */
  @SSZ private final Eth1Data eth1Data;
  /** Analogue to Eth1 Extra Data. */
  @SSZ private final Bytes32 graffiti;
  /** A list of proposer slashing challenges. */
  @SSZ(maxSizeVar = "spec.MAX_PROPOSER_SLASHINGS")
  private final ReadList<Integer, ProposerSlashing> proposerSlashings;
  /** A list of attester slashing challenges. */
  @SSZ(maxSizeVar = "spec.MAX_ATTESTER_SLASHINGS")
  private final ReadList<Integer, AttesterSlashing> attesterSlashings;
  /** A list of attestations. */
  @SSZ(maxSizeVar = "spec.MAX_ATTESTATIONS")
  private final ReadList<Integer, Attestation> attestations;
  /** A list of validator deposit proofs. */
  @SSZ(maxSizeVar = "spec.MAX_DEPOSITS")
  private final ReadList<Integer, Deposit> deposits;
  /** A list of validator exits. */
  @SSZ(maxSizeVar = "spec.MAX_VOLUNTARY_EXITS")
  private final ReadList<Integer, VoluntaryExit> voluntaryExits;
  /** A list of transfers. */
  @SSZ(maxSizeVar = "spec.MAX_TRANSFERS")
  private final ReadList<Integer, Transfer> transfers;

  public BeaconBlockBody(
      BLSSignature randaoReveal,
      Eth1Data eth1Data,
      Bytes32 graffiti,
      ReadList<Integer, ProposerSlashing> proposerSlashings,
      ReadList<Integer, AttesterSlashing> attesterSlashings,
      ReadList<Integer, Attestation> attestations,
      ReadList<Integer, Deposit> deposits,
      ReadList<Integer, VoluntaryExit> voluntaryExits,
      ReadList<Integer, Transfer> transfers,
      SpecConstants specConstants) {
    this.randaoReveal = randaoReveal;
    this.eth1Data = eth1Data;
    this.graffiti = graffiti;
    this.proposerSlashings =
        proposerSlashings.maxSize() == ReadList.VARIABLE_SIZE
            ? proposerSlashings.cappedCopy(specConstants.getMaxProposerSlashings())
            : proposerSlashings;
    this.attesterSlashings =
        attesterSlashings.maxSize() == ReadList.VARIABLE_SIZE
            ? attesterSlashings.cappedCopy(specConstants.getMaxAttesterSlashings())
            : attesterSlashings;
    this.attestations =
        attestations.maxSize() == ReadList.VARIABLE_SIZE
            ? attestations.cappedCopy(specConstants.getMaxAttestations())
            : attestations;
    this.deposits =
        deposits.maxSize() == ReadList.VARIABLE_SIZE
            ? deposits.cappedCopy(specConstants.getMaxDeposits())
            : deposits;
    this.voluntaryExits =
        voluntaryExits.maxSize() == ReadList.VARIABLE_SIZE
            ? voluntaryExits.cappedCopy(specConstants.getMaxVoluntaryExits())
            : voluntaryExits;
    this.transfers =
        transfers.maxSize() == ReadList.VARIABLE_SIZE
            ? transfers.cappedCopy(specConstants.getMaxTransfers())
            : transfers;
  }

  public BeaconBlockBody(
      BLSSignature randaoReveal,
      Eth1Data eth1Data,
      Bytes32 graffiti,
      List<ProposerSlashing> proposerSlashings,
      List<AttesterSlashing> attesterSlashings,
      List<Attestation> attestations,
      List<Deposit> deposits,
      List<VoluntaryExit> voluntaryExits,
      List<Transfer> transfers,
      SpecConstants specConstants) {
    this(
        randaoReveal,
        eth1Data,
        graffiti,
        ReadList.wrap(proposerSlashings, Integer::new, specConstants.getMaxProposerSlashings()),
        ReadList.wrap(attesterSlashings, Integer::new, specConstants.getMaxAttesterSlashings()),
        ReadList.wrap(attestations, Integer::new, specConstants.getMaxAttestations()),
        ReadList.wrap(deposits, Integer::new, specConstants.getMaxDeposits()),
        ReadList.wrap(voluntaryExits, Integer::new, specConstants.getMaxVoluntaryExits()),
        ReadList.wrap(transfers, Integer::new, specConstants.getMaxTransfers()),
        specConstants);
  }

  /** A body where all lists are empty. */
  public static BeaconBlockBody getEmpty(SpecConstants specConstants) {
    return new BeaconBlockBody(
        BLSSignature.ZERO,
        Eth1Data.EMPTY,
        Bytes32.ZERO,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        specConstants);
  }

  public BLSSignature getRandaoReveal() {
    return randaoReveal;
  }

  public Eth1Data getEth1Data() {
    return eth1Data;
  }

  public Bytes32 getGraffiti() {
    return graffiti;
  }

  public ReadList<Integer, ProposerSlashing> getProposerSlashings() {
    return proposerSlashings;
  }

  public ReadList<Integer, AttesterSlashing> getAttesterSlashings() {
    return attesterSlashings;
  }

  public ReadList<Integer, Attestation> getAttestations() {
    return attestations;
  }

  public ReadList<Integer, Deposit> getDeposits() {
    return deposits;
  }

  public ReadList<Integer, VoluntaryExit> getVoluntaryExits() {
    return voluntaryExits;
  }

  public ReadList<Integer, Transfer> getTransfers() {
    return transfers;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    BeaconBlockBody blockBody = (BeaconBlockBody) object;
    return Objects.equal(randaoReveal, blockBody.randaoReveal)
        && Objects.equal(eth1Data, blockBody.eth1Data)
        && Objects.equal(graffiti, blockBody.graffiti)
        && Objects.equal(proposerSlashings, blockBody.proposerSlashings)
        && Objects.equal(attesterSlashings, blockBody.attesterSlashings)
        && Objects.equal(attestations, blockBody.attestations)
        && Objects.equal(deposits, blockBody.deposits)
        && Objects.equal(voluntaryExits, blockBody.voluntaryExits)
        && Objects.equal(transfers, blockBody.transfers);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        randaoReveal,
        eth1Data,
        graffiti,
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits,
        transfers);
  }
}
