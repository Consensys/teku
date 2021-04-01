/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.containers.Container5;
import tech.pegasys.teku.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SyncCommitteeContribution
    extends Container5<
        SyncCommitteeContribution, SszUInt64, SszBytes32, SszUInt64, SszBitvector, SszSignature> {

  protected SyncCommitteeContribution(
      final ContainerSchema5<
              SyncCommitteeContribution,
              SszUInt64,
              SszBytes32,
              SszUInt64,
              SszBitvector,
              SszSignature>
          schema) {
    super(schema);
  }

  protected SyncCommitteeContribution(
      final ContainerSchema5<
              SyncCommitteeContribution,
              SszUInt64,
              SszBytes32,
              SszUInt64,
              SszBitvector,
              SszSignature>
          schema,
      final TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected SyncCommitteeContribution(
      final ContainerSchema5<
              SyncCommitteeContribution,
              SszUInt64,
              SszBytes32,
              SszUInt64,
              SszBitvector,
              SszSignature>
          schema,
      final SszUInt64 arg0,
      final SszBytes32 arg1,
      final SszUInt64 arg2,
      final SszBitvector arg3,
      final SszSignature arg4) {
    super(schema, arg0, arg1, arg2, arg3, arg4);
  }
}
