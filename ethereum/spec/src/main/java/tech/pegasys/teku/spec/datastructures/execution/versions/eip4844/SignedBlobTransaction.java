/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip4844;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SignedBlobTransaction
    extends Container2<SignedBlobTransaction, BlobTransaction, ECDSASignature> {

  public static class SignedBlobTransactionSchema
      extends ContainerSchema2<SignedBlobTransaction, BlobTransaction, ECDSASignature> {

    public SignedBlobTransactionSchema() {
      super(
          "SignedBlobTransaction",
          namedSchema("message", BlobTransaction.SSZ_SCHEMA),
          namedSchema("signature", ECDSASignature.SSZ_SCHEMA));
    }

    @Override
    public SignedBlobTransaction createFromBackingNode(TreeNode node) {
      return new SignedBlobTransaction(this, node);
    }
  }

  public BlobTransaction getBlobTransaction() {
    return getField0();
  }

  public static final SignedBlobTransaction.SignedBlobTransactionSchema SSZ_SCHEMA =
      new SignedBlobTransaction.SignedBlobTransactionSchema();

  SignedBlobTransaction(final SignedBlobTransactionSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }
}
