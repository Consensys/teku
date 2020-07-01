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

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArraySnaphot {

  private final UnsignedLong justifiedEpoch;
  private final UnsignedLong finalizedEpoch;
  private final List<ProtoNode> nodes;

  private ProtoArraySnaphot(
      UnsignedLong justifiedEpoch, UnsignedLong finalizedEpoch, List<ProtoNode> nodes) {
    this.justifiedEpoch = justifiedEpoch;
    this.finalizedEpoch = finalizedEpoch;
    this.nodes = nodes;
  }

  public static ProtoArraySnaphot save(final ProtoArray protoArray) {
    List<ProtoNode> nodes =
        protoArray.getNodes().stream().map(ProtoNode::cloneForSaving).collect(Collectors.toList());
    UnsignedLong justifiedEpoch = protoArray.getJustifiedEpoch();
    UnsignedLong finalizedEpoch = protoArray.getFinalizedEpoch();
    return new ProtoArraySnaphot(justifiedEpoch, finalizedEpoch, nodes);
  }

  public ProtoArray toProtoArray() {
    return new ProtoArray(
        Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD,
        justifiedEpoch,
        finalizedEpoch,
        nodes,
        new HashMap<>());
  }
}
