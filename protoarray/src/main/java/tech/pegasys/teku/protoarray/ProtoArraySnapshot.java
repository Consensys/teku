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

package tech.pegasys.teku.protoarray;

import com.google.common.base.Objects;
import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArraySnapshot {

  private final UnsignedLong justifiedEpoch;
  private final UnsignedLong finalizedEpoch;
  private final List<BlockInformation> blockInformationList;

  public ProtoArraySnapshot(
      final UnsignedLong justifiedEpoch,
      final UnsignedLong finalizedEpoch,
      final List<BlockInformation> blockInformationList) {
    this.justifiedEpoch = justifiedEpoch;
    this.finalizedEpoch = finalizedEpoch;
    this.blockInformationList = blockInformationList;
  }

  public static ProtoArraySnapshot create(final ProtoArray protoArray) {
    List<BlockInformation> nodes =
        protoArray.getNodes().stream()
            .map(ProtoNode::createBlockInformation)
            .collect(Collectors.toList());
    UnsignedLong justifiedEpoch = protoArray.getJustifiedEpoch();
    UnsignedLong finalizedEpoch = protoArray.getFinalizedEpoch();
    return new ProtoArraySnapshot(justifiedEpoch, finalizedEpoch, nodes);
  }

  public ProtoArray toProtoArray() {
    return new ProtoArray(
        Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD,
        justifiedEpoch,
        finalizedEpoch,
        blockInformationList.stream()
            .map(BlockInformation::toProtoNode)
            .collect(Collectors.toList()),
        new HashMap<>());
  }

  public UnsignedLong getJustifiedEpoch() {
    return justifiedEpoch;
  }

  public UnsignedLong getFinalizedEpoch() {
    return finalizedEpoch;
  }

  public List<BlockInformation> getBlockInformationList() {
    return blockInformationList;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProtoArraySnapshot)) return false;
    ProtoArraySnapshot that = (ProtoArraySnapshot) o;
    return Objects.equal(getJustifiedEpoch(), that.getJustifiedEpoch())
        && Objects.equal(getFinalizedEpoch(), that.getFinalizedEpoch())
        && Objects.equal(getBlockInformationList(), that.getBlockInformationList());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getJustifiedEpoch(), getFinalizedEpoch(), getBlockInformationList());
  }
}
