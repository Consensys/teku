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

package tech.pegasys.teku.api.migrated;

import java.util.List;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class AllBlocksAtSlotData {
  private final SpecMilestone version;
  private final List<SignedBeaconBlock> blocks;

  public AllBlocksAtSlotData(final SpecMilestone version, final List<SignedBeaconBlock> blocks) {
    this.version = version;
    this.blocks = blocks;
  }

  public SpecMilestone getVersion() {
    return version;
  }

  public List<SignedBeaconBlock> getBlocks() {
    return blocks;
  }
}
