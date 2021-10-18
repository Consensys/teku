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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;

public interface BeaconBlockBodySchemaAltair<T extends BeaconBlockBodyAltair>
    extends BeaconBlockBodySchema<T> {

  static BeaconBlockBodySchemaAltair<? extends BeaconBlockBodyAltair> create(
      final SpecConfig specConfig) {

    return BeaconBlockBodySchemaAltairImpl.create(
        specConfig.getMaxProposerSlashings(),
        specConfig.getMaxAttesterSlashings(),
        specConfig.getMaxAttestations(),
        specConfig.getMaxDeposits(),
        specConfig.getMaxVoluntaryExits(),
        specConfig.toVersionAltair().orElseThrow().getSyncCommitteeSize());
  }

  static BeaconBlockBodySchemaAltair<?> required(final BeaconBlockBodySchema<?> schema) {
    checkArgument(
        schema instanceof BeaconBlockBodySchemaAltair,
        "Expected a BeaconBlockBodySchemaAltair but was %s",
        schema.getClass());
    return (BeaconBlockBodySchemaAltair<?>) schema;
  }

  SyncAggregateSchema getSyncAggregateSchema();

  @Override
  default Optional<BeaconBlockBodySchemaAltair<?>> toVersionAltair() {
    return Optional.of(this);
  }
}
