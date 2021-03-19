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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Supplier;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyContent;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyContent;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.ssz.primitive.SszBytes32;

public class BeaconBlockBodyContentAltair extends AbstractBeaconBlockBodyContent {

  private BeaconBlockBodySchemaAltair schema;
  private SyncAggregate syncAggregate;

  public BeaconBlockBodyContentAltair schema(final BeaconBlockBodySchemaAltair schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public BeaconBlockBodyContent syncAggregate(final Supplier<SyncAggregate> syncAggregateSupplier) {
    this.syncAggregate = syncAggregateSupplier.get();
    return this;
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(schema, "schema must be specified");
    checkNotNull(syncAggregate, "syncAggregate must be specified");
  }

  public BeaconBlockBodyAltair build() {
    validate();

    return new BeaconBlockBodyAltair(
        schema,
        new SszSignature(randaoReveal),
        eth1Data,
        SszBytes32.of(graffiti),
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits,
        syncAggregate);
  }
}
