/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_HEADER_SCHEMA;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SignedExecutionPayloadHeaderSchema
    extends ContainerSchema2<SignedExecutionPayloadHeader, ExecutionPayloadHeader, SszSignature> {

  public SignedExecutionPayloadHeaderSchema(final SchemaRegistry schemaRegistry) {
    super(
        "SignedExecutionPayloadHeader",
        namedSchema(
            "message",
            SszSchema.as(
                ExecutionPayloadHeader.class, schemaRegistry.get(EXECUTION_PAYLOAD_HEADER_SCHEMA))),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public SignedExecutionPayloadHeader create(
      final ExecutionPayloadHeader message, final BLSSignature signature) {
    return new SignedExecutionPayloadHeader(this, message, signature);
  }

  @Override
  public SignedExecutionPayloadHeader createFromBackingNode(final TreeNode node) {
    return new SignedExecutionPayloadHeader(this, node);
  }

  public ExecutionPayloadHeaderSchema<?> getMessageSchema() {
    return (ExecutionPayloadHeaderSchema<?>) getChildSchema(getFieldIndex("message"));
  }

  public long getBlobKzgCommitmentsRootGeneralizedIndex() {
    return GIndexUtil.gIdxCompose(
        getChildGeneralizedIndex(getFieldIndex("message")),
        getMessageSchema().toVersionGloasRequired().getBlobKzgCommitmentsRootGeneralizedIndex());
  }
}
