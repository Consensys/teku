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

package tech.pegasys.teku.spec.datastructures.type;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.kzg.KZGCommitment;

public class SszKZGCommitment extends SszByteVectorImpl {

  private final Supplier<KZGCommitment> kzgCommitment;

  public SszKZGCommitment(final Bytes48 kzgCommitmentBytes) {
    super(SszKZGCommitmentSchema.INSTANCE, kzgCommitmentBytes);
    this.kzgCommitment = Suppliers.memoize(this::createKZGCommitment);
  }

  public SszKZGCommitment(final KZGCommitment kzgCommitment) {
    super(SszKZGCommitmentSchema.INSTANCE, kzgCommitment.getBytesCompressed());
    this.kzgCommitment = Suppliers.memoize(this::createKZGCommitment);
  }

  SszKZGCommitment(final TreeNode backingNode) {
    super(SszKZGCommitmentSchema.INSTANCE, backingNode);
    this.kzgCommitment = Suppliers.memoize(this::createKZGCommitment);
  }

  public KZGCommitment getKZGCommitment() {
    return kzgCommitment.get();
  }

  private KZGCommitment createKZGCommitment() {
    return KZGCommitment.fromBytesCompressed(getBytes());
  }

  @Override
  public Bytes48 getBytes() {
    return Bytes48.wrap(super.getBytes());
  }

  @Override
  public SszKZGCommitmentSchema getSchema() {
    return (SszKZGCommitmentSchema) super.getSchema();
  }
}
