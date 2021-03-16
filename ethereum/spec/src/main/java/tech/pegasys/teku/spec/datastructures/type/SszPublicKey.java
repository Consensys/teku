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
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ssz.backing.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class SszPublicKey extends SszByteVectorImpl {

  private final Supplier<BLSPublicKey> publicKey;

  public SszPublicKey(Bytes48 publicKeyBytes) {
    super(SszPublicKeySchema.INSTANCE, publicKeyBytes);
    this.publicKey = Suppliers.memoize(this::createBLSPublicKey);
  }

  public SszPublicKey(BLSPublicKey publicKey) {
    super(SszPublicKeySchema.INSTANCE, publicKey.toBytesCompressed());
    this.publicKey = () -> publicKey;
  }

  SszPublicKey(TreeNode backingNode) {
    super(SszPublicKeySchema.INSTANCE, backingNode);
    this.publicKey = Suppliers.memoize(this::createBLSPublicKey);
  }

  public BLSPublicKey getBLSPublicKey() {
    return publicKey.get();
  }

  private BLSPublicKey createBLSPublicKey() {
    return BLSPublicKey.fromBytesCompressed(getBytes());
  }

  @Override
  public Bytes48 getBytes() {
    return Bytes48.wrap(super.getBytes());
  }
}
