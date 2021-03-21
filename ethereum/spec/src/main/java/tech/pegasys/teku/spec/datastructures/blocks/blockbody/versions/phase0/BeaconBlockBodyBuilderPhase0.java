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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Supplier;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.ssz.primitive.SszBytes32;

public class BeaconBlockBodyBuilderPhase0 extends AbstractBeaconBlockBodyBuilder {
  private BeaconBlockBodySchemaPhase0 schema;

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
  protected void validate() {
    super.validate();
    checkNotNull(schema, "schema must be specified");
  }

  public BeaconBlockBodyPhase0 build() {
    validate();
    return new BeaconBlockBodyPhase0(
        schema,
        new SszSignature(randaoReveal),
        eth1Data,
        SszBytes32.of(graffiti),
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits);
  }
}
