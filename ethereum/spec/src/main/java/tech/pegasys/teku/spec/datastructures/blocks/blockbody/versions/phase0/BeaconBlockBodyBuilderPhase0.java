/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderPhase0 implements BeaconBlockBodyBuilder {
  private BeaconBlockBodySchemaPhase0 schema;
  protected BLSSignature randaoReveal;
  protected Eth1Data eth1Data;
  protected Bytes32 graffiti;
  protected SszList<Attestation> attestations;
  protected SszList<ProposerSlashing> proposerSlashings;
  protected SszList<AttesterSlashing> attesterSlashings;
  protected SszList<Deposit> deposits;
  protected SszList<SignedVoluntaryExit> voluntaryExits;

  @Override
  public BeaconBlockBodyBuilder randaoReveal(final BLSSignature randaoReveal) {
    this.randaoReveal = randaoReveal;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder eth1Data(final Eth1Data eth1Data) {
    this.eth1Data = eth1Data;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder graffiti(final Bytes32 graffiti) {
    this.graffiti = graffiti;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder attestations(final SszList<Attestation> attestations) {
    this.attestations = attestations;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder proposerSlashings(
      final SszList<ProposerSlashing> proposerSlashings) {
    this.proposerSlashings = proposerSlashings;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder attesterSlashings(
      final SszList<AttesterSlashing> attesterSlashings) {
    this.attesterSlashings = attesterSlashings;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder deposits(final SszList<Deposit> deposits) {
    this.deposits = deposits;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder voluntaryExits(final SszList<SignedVoluntaryExit> voluntaryExits) {
    this.voluntaryExits = voluntaryExits;
    return this;
  }

  public BeaconBlockBodyBuilderPhase0 schema(final BeaconBlockBodySchemaPhase0 schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder syncAggregate(final Supplier<SyncAggregate> syncAggregateSupplier) {
    // No sync aggregate in phase 0
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder executionPayload(
      Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
    // No execution payload in phase 0
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder executionPayloadHeader(
      Supplier<SafeFuture<ExecutionPayloadHeader>> executionPayloadHeaderSupplier) {
    // No execution payload in phase 0
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder blsToExecutionChanges(
      final Supplier<SszList<SignedBlsToExecutionChange>> blsToExecutionChanges) {
    // No BlsToExecutionChange in phase 0
    return this;
  }

  protected void validateSchema() {
    checkNotNull(schema, "schema must be specified");
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
    validateSchema();
  }

  @Override
  public SafeFuture<BeaconBlockBody> build() {
    validate();
    return SafeFuture.completedFuture(
        new BeaconBlockBodyPhase0(
            schema,
            new SszSignature(randaoReveal),
            eth1Data,
            SszBytes32.of(graffiti),
            proposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits));
  }
}
