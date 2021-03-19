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

import java.util.function.Consumer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyContent;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;

public class BeaconBlockBodyPhase0Test extends AbstractBeaconBlockBodyTest<BeaconBlockBodyPhase0> {

  @Override
  protected BeaconBlockBodyPhase0 createBlockBody(
      final Consumer<BeaconBlockBodyContent> contentProvider) {
    return getBlockBodySchema().createBlockBody(contentProvider);
  }

  @Override
  protected BeaconBlockBodySchema<BeaconBlockBodyPhase0> getBlockBodySchema() {
    return BeaconBlockBodySchemaPhase0.create(spec.getGenesisSpecConfig());
  }
}
