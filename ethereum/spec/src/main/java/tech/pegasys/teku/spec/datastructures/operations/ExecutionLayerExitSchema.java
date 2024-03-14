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

package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class ExecutionLayerExitSchema
    extends ContainerSchema2<ExecutionLayerExit, SszByteVector, SszPublicKey> {

  public ExecutionLayerExitSchema() {
    super(
        "ExecutionLayerExit",
        namedSchema("source_address", SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema("validator_pubkey", SszPublicKeySchema.INSTANCE));
  }

  public ExecutionLayerExit create(
      final Bytes20 sourceAddress, final BLSPublicKey validatorPublicKey) {
    return new ExecutionLayerExit(this, sourceAddress, validatorPublicKey);
  }

  @Override
  public ExecutionLayerExit createFromBackingNode(final TreeNode node) {
    return new ExecutionLayerExit(this, node);
  }
}
