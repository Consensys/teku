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

import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class ValidatorRegistrationV1
    extends Container4<ValidatorRegistrationV1, SszByteVector, SszUInt64, SszUInt64, SszPublicKey> {

  protected ValidatorRegistrationV1(ValidatorRegistrationV1Schema schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected ValidatorRegistrationV1(
      ValidatorRegistrationV1Schema schema,
      SszByteVector feeRecipient,
      SszUInt64 gasTarget,
      SszUInt64 timestamp,
      SszPublicKey publicKey) {
    super(schema, feeRecipient, gasTarget, timestamp, publicKey);
  }
}
