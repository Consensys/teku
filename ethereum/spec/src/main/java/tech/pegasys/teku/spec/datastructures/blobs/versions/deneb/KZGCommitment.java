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

package tech.pegasys.teku.spec.datastructures.blobs.versions.deneb;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;

public class KZGCommitment extends SszByteVectorImpl {

  public KZGCommitment(final Bytes48 bytes) {
    super(KZGCommitmentSchema.INSTANCE, bytes);
  }

  KZGCommitment(final KZGCommitmentSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  @Override
  public Bytes48 getBytes() {
    return (Bytes48) super.getBytes();
  }

  @Override
  public KZGCommitmentSchema getSchema() {
    return (KZGCommitmentSchema) super.getSchema();
  }

  public String toAbbreviatedString() {
    return getBytes().slice(0, 7).toUnprefixedHexString();
  }

  public static KZGCommitment fromSSZBytes(final Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new KZGCommitment(
                Bytes48.wrap(
                    reader.readFixedBytes(SpecConfigDeneb.BYTES_PER_COMMITMENT.intValue()))));
  }

  public static KZGCommitment fromHexString(final String hexString) {
    return new KZGCommitment(Bytes48.fromHexString(hexString));
  }
}
