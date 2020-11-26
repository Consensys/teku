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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArraySnapshot {

  private final UInt64 justifiedEpoch;
  private final UInt64 finalizedEpoch;
  private final UInt64 initialEpoch;
  private final List<BlockInformation> blockInformationList;

  public ProtoArraySnapshot(
      final UInt64 justifiedEpoch,
      final UInt64 finalizedEpoch,
      final UInt64 initialEpoch,
      final List<BlockInformation> blockInformationList) {
    this.justifiedEpoch = justifiedEpoch;
    this.finalizedEpoch = finalizedEpoch;
    this.initialEpoch = initialEpoch;
    this.blockInformationList = blockInformationList;
  }

  public static ProtoArraySnapshot create(final ProtoArray protoArray) {
    List<BlockInformation> nodes =
        protoArray.getNodes().stream()
            .map(ProtoNode::createBlockInformation)
            .collect(Collectors.toList());
    UInt64 justifiedEpoch = protoArray.getJustifiedEpoch();
    UInt64 finalizedEpoch = protoArray.getFinalizedEpoch();
    UInt64 initialEpoch = protoArray.getInitialEpoch();
    return new ProtoArraySnapshot(justifiedEpoch, finalizedEpoch, initialEpoch, nodes);
  }

  public ProtoArray toProtoArray() {
    ProtoArray protoArray =
        new ProtoArray(
            Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD,
            justifiedEpoch,
            finalizedEpoch,
            initialEpoch,
            new ArrayList<>(),
            new HashMap<>());

    blockInformationList.forEach(
        blockInformation ->
            protoArray.onBlock(
                blockInformation.getBlockSlot(),
                blockInformation.getBlockRoot(),
                blockInformation.getParentRoot(),
                blockInformation.getStateRoot(),
                blockInformation.getJustifiedEpoch(),
                blockInformation.getFinalizedEpoch()));
    return protoArray;
  }

  public UInt64 getJustifiedEpoch() {
    return justifiedEpoch;
  }

  public UInt64 getFinalizedEpoch() {
    return finalizedEpoch;
  }

  public UInt64 getInitialEpoch() {
    return initialEpoch;
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
