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

package tech.pegasys.teku.spec.datastructures.type;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszSignature extends SszByteVectorImpl {

  private final Supplier<BLSSignature> signature;

  public SszSignature(BLSSignature signature) {
    super(SszSignatureSchema.INSTANCE, signature.toBytesCompressed());
    this.signature = () -> signature;
  }

  SszSignature(TreeNode backingNode) {
    super(SszSignatureSchema.INSTANCE, backingNode);
    signature = Suppliers.memoize(this::createBLSSignature);
  }

  public BLSSignature getSignature() {
    return signature.get();
  }

  private BLSSignature createBLSSignature() {
    return BLSSignature.fromBytesCompressed(getBytes());
  }
}
