/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.lightclient;

import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class LightClientUpdateResponse
    extends Container3<LightClientUpdateResponse, SszUInt64, SszBytes4, LightClientUpdate> {

  public LightClientUpdateResponse(
      final LightClientUpdateResponseSchema schema,
      final SszUInt64 responseChunkLen,
      final SszBytes4 context,
      final LightClientUpdate payload) {
    super(schema, responseChunkLen, context, payload);
  }

  protected LightClientUpdateResponse(
      final LightClientUpdateResponseSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }
}
