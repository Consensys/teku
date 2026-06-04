/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.api.migrated;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.ByteUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;

public class StateBuilderData extends Container3<StateBuilderData, SszUInt64, SszByte, Builder> {
  public static final int STATUS_PENDING = 0;
  public static final int STATUS_ACTIVE = 1;
  public static final int STATUS_EXITED = 2;

  public static final StateBuilderDataSchema SSZ_SCHEMA = new StateBuilderDataSchema();

  @SuppressWarnings("unchecked")
  public static final SszListSchema<StateBuilderData, SszList<StateBuilderData>> SSZ_LIST_SCHEMA =
      (SszListSchema<StateBuilderData, SszList<StateBuilderData>>)
          SszListSchema.create(StateBuilderData.SSZ_SCHEMA, Integer.MAX_VALUE);

  protected StateBuilderData(
      final ContainerSchema3<StateBuilderData, SszUInt64, SszByte, Builder> schema,
      final SszUInt64 index,
      final SszByte status,
      final Builder builder) {
    super(schema, index, status, builder);
  }

  protected StateBuilderData(
      final ContainerSchema3<StateBuilderData, SszUInt64, SszByte, Builder> schema,
      final TreeNode node) {
    super(schema, node);
  }

  public static StateBuilderData create(
      final UInt64 index, final int status, final Builder builder) {
    checkArgument(
        status == STATUS_PENDING || status == STATUS_ACTIVE || status == STATUS_EXITED,
        "Invalid builder status: %s",
        status);
    return new StateBuilderData(SSZ_SCHEMA, SszUInt64.of(index), SszByte.asUInt8(status), builder);
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public int getStatus() {
    return ByteUtil.toUnsignedInt(getField1().get());
  }

  public Builder getBuilder() {
    return getField2();
  }
}
