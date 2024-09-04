/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip7732;

import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOB_KZG_COMMITMENTS_ROOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BUILDER_INDEX;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.GAS_LIMIT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.PARENT_BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.PARENT_BLOCK_ROOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.SLOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.VALUE;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema8;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;

public class ExecutionPayloadHeaderSchemaEip7732
    extends ContainerSchema8<
        ExecutionPayloadHeaderEip7732Impl,
        SszBytes32,
        SszBytes32,
        SszBytes32,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszBytes32>
    implements ExecutionPayloadHeaderSchema<ExecutionPayloadHeaderEip7732Impl> {

  private final ExecutionPayloadHeaderEip7732Impl defaultExecutionPayloadHeader;
  private final ExecutionPayloadHeaderEip7732Impl executionPayloadHeaderOfDefaultPayload;

  public ExecutionPayloadHeaderSchemaEip7732(final SpecConfigEip7732 specConfig) {
    super(
        "ExecutionPayloadHeaderEip7732",
        namedSchema(PARENT_BLOCK_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(PARENT_BLOCK_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(BLOCK_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(GAS_LIMIT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(BUILDER_INDEX, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(SLOT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(VALUE, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(BLOB_KZG_COMMITMENTS_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA));

    final ExecutionPayloadEip7732Impl defaultExecutionPayload =
        new ExecutionPayloadSchemaEip7732(specConfig).getDefault();

    this.executionPayloadHeaderOfDefaultPayload =
        createFromExecutionPayload(defaultExecutionPayload);
    this.defaultExecutionPayloadHeader = createFromBackingNode(getDefaultTree());
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    return LongList.of(getChildGeneralizedIndex(getFieldIndex(BLOB_KZG_COMMITMENTS_ROOT)));
  }

  @Override
  public ExecutionPayloadHeader createExecutionPayloadHeader(
      final Consumer<ExecutionPayloadHeaderBuilder> builderConsumer) {
    final ExecutionPayloadHeaderBuilderEip7732 builder =
        new ExecutionPayloadHeaderBuilderEip7732().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public ExecutionPayloadHeaderEip7732Impl getDefault() {
    return defaultExecutionPayloadHeader;
  }

  @Override
  public ExecutionPayloadHeaderEip7732 getHeaderOfDefaultPayload() {
    return executionPayloadHeaderOfDefaultPayload;
  }

  @Override
  public ExecutionPayloadHeaderEip7732Impl createFromBackingNode(final TreeNode node) {
    return new ExecutionPayloadHeaderEip7732Impl(this, node);
  }

  @Override
  public ExecutionPayloadHeaderEip7732Impl createFromExecutionPayload(
      final ExecutionPayload payload) {
    return new ExecutionPayloadHeaderEip7732Impl(
        this,
        SszBytes32.of(Bytes32.ZERO),
        SszBytes32.of(Bytes32.ZERO),
        SszBytes32.of(payload.getBlockHash()),
        SszUInt64.of(payload.getGasLimit()),
        SszUInt64.of(UInt64.ZERO),
        SszUInt64.of(UInt64.ZERO),
        SszUInt64.of(UInt64.ZERO),
        SszBytes32.of(Bytes32.ZERO));
  }
}
