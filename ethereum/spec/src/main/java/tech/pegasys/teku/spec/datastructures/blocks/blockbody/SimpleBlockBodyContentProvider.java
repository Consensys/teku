/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.ssz.SszList;

public class SimpleBlockBodyContentProvider implements BlockBodyContentProvider {

  private final BLSSignature randaoReveal;
  private final Eth1Data eth1Data;
  private final Bytes32 graffiti;
  private final SszList<Attestation> attestations;
  private final SszList<ProposerSlashing> proposerSlashings;
  private final SszList<AttesterSlashing> attesterSlashings;
  private final SszList<Deposit> deposits;
  private final SszList<SignedVoluntaryExit> voluntaryExits;
  private final Optional<SyncAggregate> syncAggregate;

  public SimpleBlockBodyContentProvider(
      final BLSSignature randaoReveal,
      final Eth1Data eth1Data,
      final Bytes32 graffiti,
      final SszList<Attestation> attestations,
      final SszList<ProposerSlashing> proposerSlashings,
      final SszList<AttesterSlashing> attesterSlashings,
      final SszList<Deposit> deposits,
      final SszList<SignedVoluntaryExit> voluntaryExits) {
    this.randaoReveal = randaoReveal;
    this.eth1Data = eth1Data;
    this.graffiti = graffiti;
    this.attestations = attestations;
    this.proposerSlashings = proposerSlashings;
    this.attesterSlashings = attesterSlashings;
    this.deposits = deposits;
    this.voluntaryExits = voluntaryExits;
    this.syncAggregate = Optional.empty();
  }

  public SimpleBlockBodyContentProvider(
      final BLSSignature randaoReveal,
      final Eth1Data eth1Data,
      final Bytes32 graffiti,
      final SszList<Attestation> attestations,
      final SszList<ProposerSlashing> proposerSlashings,
      final SszList<AttesterSlashing> attesterSlashings,
      final SszList<Deposit> deposits,
      final SszList<SignedVoluntaryExit> voluntaryExits,
      final SyncAggregate syncAggregate) {
    this.randaoReveal = randaoReveal;
    this.eth1Data = eth1Data;
    this.graffiti = graffiti;
    this.attestations = attestations;
    this.proposerSlashings = proposerSlashings;
    this.attesterSlashings = attesterSlashings;
    this.deposits = deposits;
    this.voluntaryExits = voluntaryExits;
    this.syncAggregate = Optional.of(syncAggregate);
  }

  @Override
  public BLSSignature getRandaoReveal() {
    return randaoReveal;
  }

  @Override
  public Eth1Data getEth1Data() {
    return eth1Data;
  }

  @Override
  public Bytes32 getGraffiti() {
    return graffiti;
  }

  @Override
  public SszList<Attestation> getAttestations() {
    return attestations;
  }

  @Override
  public SszList<ProposerSlashing> getProposerSlashings() {
    return proposerSlashings;
  }

  @Override
  public SszList<AttesterSlashing> getAttesterSlashings() {
    return attesterSlashings;
  }

  @Override
  public SszList<Deposit> getDeposits() {
    return deposits;
  }

  @Override
  public SszList<SignedVoluntaryExit> getVoluntaryExits() {
    return voluntaryExits;
  }

  @Override
  public Optional<SyncAggregate> getSyncAggregate() {
    return syncAggregate;
  }
}
