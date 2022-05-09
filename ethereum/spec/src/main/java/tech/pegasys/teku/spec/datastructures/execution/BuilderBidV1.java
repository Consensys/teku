/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.execution;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BuilderBidV1
    extends Container3<BuilderBidV1, ExecutionPayloadHeader, SszUInt256, SszPublicKey> {

  protected BuilderBidV1(BuilderBidV1Schema schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected BuilderBidV1(
      BuilderBidV1Schema schema,
      ExecutionPayloadHeader executionPayloadHeader,
      SszUInt256 value,
      SszPublicKey publicKey) {
    super(schema, executionPayloadHeader, value, publicKey);
  }

  public ExecutionPayloadHeader getExecutionPayloadHeader() {
    return getField0();
  }

  public UInt256 getValue() {
    return getField1().get();
  }

  public BLSPublicKey getPublicKey() {
    return getField2().getBLSPublicKey();
  }
}
