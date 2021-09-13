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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;

public interface BeaconBlockBodySchemaMerge<T extends BeaconBlockBodyMerge>
    extends BeaconBlockBodySchemaAltair<T> {

  static BeaconBlockBodySchemaMerge<?> create(final SpecConfig specConfig) {
    return SpecConfigMerge.required(
        specConfig,
        config ->
            BeaconBlockBodySchemaMergeImpl.create(
                config.getMaxProposerSlashings(),
                config.getMaxAttesterSlashings(),
                config.getMaxAttestations(),
                config.getMaxDeposits(),
                config.getMaxVoluntaryExits(),
                // TODO SpecConfigMerge inherits SpecConfigAltair
                config.toVersionAltair().orElseThrow().getSyncCommitteeSize()));
  }

  @Override
  default Optional<BeaconBlockBodySchemaMerge<?>> toVersionMerge() {
    return Optional.of(this);
  }
}
