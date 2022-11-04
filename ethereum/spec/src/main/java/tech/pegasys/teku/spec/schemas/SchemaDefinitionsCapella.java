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

package tech.pegasys.teku.spec.schemas;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;

public class SchemaDefinitionsCapella extends SchemaDefinitionsBellatrix {

  private final BeaconStateSchemaCapella beaconStateSchema;
  private final WithdrawalSchema withdrawalSchema;

  private final BlsToExecutionChangeSchema blsToExecutionChangeSchema;

  private final SignedBlsToExecutionChangeSchema signedBlsToExecutionChangeSchema;

  public SchemaDefinitionsCapella(final SpecConfigCapella specConfig) {
    super(specConfig.toVersionCapella().orElseThrow());
    this.beaconStateSchema = BeaconStateSchemaCapella.create(specConfig);
    this.withdrawalSchema = new WithdrawalSchema();
    this.blsToExecutionChangeSchema = new BlsToExecutionChangeSchema();
    this.signedBlsToExecutionChangeSchema = new SignedBlsToExecutionChangeSchema();
  }

  public static SchemaDefinitionsCapella required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsCapella,
        "Expected definitions of type %s by got %s",
        SchemaDefinitionsCapella.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsCapella) schemaDefinitions;
  }

  @Override
  public BeaconStateSchema<? extends BeaconStateCapella, ? extends MutableBeaconStateCapella>
      getBeaconStateSchema() {
    return beaconStateSchema;
  }

  public WithdrawalSchema getWithdrawalSchema() {
    return withdrawalSchema;
  }

  public BlsToExecutionChangeSchema getBlsToExecutionChangeSchema() {
    return blsToExecutionChangeSchema;
  }

  public SignedBlsToExecutionChangeSchema getSignedBlsToExecutionChangeSchema() {
    return signedBlsToExecutionChangeSchema;
  }

  public Optional<SchemaDefinitionsCapella> toVersionCapella() {
    return Optional.of(this);
  }
}
