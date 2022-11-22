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

package tech.pegasys.teku.networking.eth2;

import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;

public class BlockAndBlobsSidecarOperationMilestoneValidator
    implements OperationMilestoneValidator<SignedBeaconBlockAndBlobsSidecar> {

  private final Bytes4 forkDigest;

  public BlockAndBlobsSidecarOperationMilestoneValidator(final Spec spec, final ForkInfo forkInfo) {
    this.forkDigest = forkInfo.getForkDigest(spec);
  }

  @Override
  public boolean isValid(final SignedBeaconBlockAndBlobsSidecar message, final Bytes4 forkDigest) {
    return this.forkDigest.equals(forkDigest);
  }
}
