/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.fuzz.input;

import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public class ProposerSlashingFuzzInputTest
    extends AbstractFuzzInputTest<ProposerSlashingFuzzInput> {

  @Override
  protected SszSchema<ProposerSlashingFuzzInput> getInputType() {
    return ProposerSlashingFuzzInput.createType();
  }

  @Override
  protected ProposerSlashingFuzzInput createInput() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    return new ProposerSlashingFuzzInput(state, slashing);
  }
}
