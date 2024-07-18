/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class DasSamplerManager {
  public static final DasSamplerManager NOOP =
      new DasSamplerManager().setSampler(DataAvailabilitySampler.NOOP);
  private volatile DataAvailabilitySampler dataAvailabilitySampler;

  public DasSamplerManager setSampler(final DataAvailabilitySampler dataAvailabilitySampler) {
    this.dataAvailabilitySampler = dataAvailabilitySampler;
    return this;
  }

  public SafeFuture<Void> checkDataAvailability(final SignedBeaconBlock block) {
    final boolean isCheckRequired =
        block
            .getBeaconBlock()
            .flatMap(beaconBlock -> beaconBlock.getBody().toVersionEip7594())
            .map(bodyEip7594 -> !bodyEip7594.getBlobKzgCommitments().isEmpty())
            .orElse(false);
    if (!isCheckRequired) {
      return SafeFuture.COMPLETE;
    }
    if (dataAvailabilitySampler == null) {
      throw new RuntimeException("Not initialized!");
    }
    return dataAvailabilitySampler.checkDataAvailability(
        block.getSlot(), block.getRoot(), block.getParentRoot());
  }
}
