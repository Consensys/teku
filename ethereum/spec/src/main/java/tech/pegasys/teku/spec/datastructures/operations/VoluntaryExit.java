/*
 * Copyright 2019 ConsenSys AG.
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

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class VoluntaryExit extends Container2<VoluntaryExit, SszUInt64, SszUInt64> {

  public static class VoluntaryExitSchema
      extends ContainerSchema2<VoluntaryExit, SszUInt64, SszUInt64> {

    public VoluntaryExitSchema() {
      super(
          "VoluntaryExit",
          namedSchema("epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public VoluntaryExit createFromBackingNode(TreeNode node) {
      return new VoluntaryExit(this, node);
    }
  }

  public static final VoluntaryExitSchema SSZ_SCHEMA = new VoluntaryExitSchema();

  private VoluntaryExit(VoluntaryExitSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public VoluntaryExit(UInt64 epoch, UInt64 validatorIndex) {
    super(SSZ_SCHEMA, SszUInt64.of(epoch), SszUInt64.of(validatorIndex));
  }

  public UInt64 getEpoch() {
    return getField0().get();
  }

  public UInt64 getValidatorIndex() {
    return getField1().get();
  }
}
