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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.common;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyContent;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.ssz.SszList;

public abstract class AbstractBeaconBlockBodyContent implements BeaconBlockBodyContent {

  protected BLSSignature randaoReveal;
  protected Eth1Data eth1Data;
  protected Bytes32 graffiti;
  protected SszList<Attestation> attestations;
  protected SszList<ProposerSlashing> proposerSlashings;
  protected SszList<AttesterSlashing> attesterSlashings;
  protected SszList<Deposit> deposits;
  protected SszList<SignedVoluntaryExit> voluntaryExits;

  @Override
  public BeaconBlockBodyContent randaoReveal(final BLSSignature randaoReveal) {
    this.randaoReveal = randaoReveal;
    return this;
  }

  @Override
  public BeaconBlockBodyContent eth1Data(final Eth1Data eth1Data) {
    this.eth1Data = eth1Data;
    return this;
  }

  @Override
  public BeaconBlockBodyContent graffiti(final Bytes32 graffiti) {
    this.graffiti = graffiti;
    return this;
  }

  @Override
  public BeaconBlockBodyContent attestations(final SszList<Attestation> attestations) {
    this.attestations = attestations;
    return this;
  }

  @Override
  public BeaconBlockBodyContent proposerSlashings(
      final SszList<ProposerSlashing> proposerSlashings) {
    this.proposerSlashings = proposerSlashings;
    return this;
  }

  @Override
  public BeaconBlockBodyContent attesterSlashings(
      final SszList<AttesterSlashing> attesterSlashings) {
    this.attesterSlashings = attesterSlashings;
    return this;
  }

  @Override
  public BeaconBlockBodyContent deposits(final SszList<Deposit> deposits) {
    this.deposits = deposits;
    return this;
  }

  @Override
  public BeaconBlockBodyContent voluntaryExits(final SszList<SignedVoluntaryExit> voluntaryExits) {
    this.voluntaryExits = voluntaryExits;
    return this;
  }

  protected void validate() {
    checkNotNull(randaoReveal, "randaoReveal must be specified");
    checkNotNull(eth1Data, "eth1Data must be specified");
    checkNotNull(graffiti, "graffiti must be specified");
    checkNotNull(attestations, "attestations must be specified");
    checkNotNull(proposerSlashings, "proposerSlashings must be specified");
    checkNotNull(attesterSlashings, "attesterSlashings must be specified");
    checkNotNull(deposits, "deposits must be specified");
    checkNotNull(voluntaryExits, "voluntaryExits must be specified");
  }
}
