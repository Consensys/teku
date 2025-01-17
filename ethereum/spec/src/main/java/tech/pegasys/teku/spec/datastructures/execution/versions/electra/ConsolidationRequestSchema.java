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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class ConsolidationRequestSchema
    extends ContainerSchema3<ConsolidationRequest, SszByteVector, SszPublicKey, SszPublicKey> {

  public ConsolidationRequestSchema() {
    super(
        "ConsolidationRequest",
        namedSchema("source_address", SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema("source_pubkey", SszPublicKeySchema.INSTANCE),
        namedSchema("target_pubkey", SszPublicKeySchema.INSTANCE));
  }

  public ConsolidationRequest create(
      final Bytes20 sourceAddress,
      final BLSPublicKey sourcePubkey,
      final BLSPublicKey targetPubkey) {
    return new ConsolidationRequest(this, sourceAddress, sourcePubkey, targetPubkey);
  }

  @Override
  public ConsolidationRequest createFromBackingNode(final TreeNode node) {
    return new ConsolidationRequest(this, node);
  }
}
