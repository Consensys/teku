/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.execution.verkle;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes31;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class VerkleProof
    extends Container5<
        VerkleProof,
        SszList<SszByteVector>,
        SszList<SszByte>,
        SszList<SszBytes32>,
        SszBytes32,
        IpaProof> {

  VerkleProof(final VerkleProofSchema verkleProofSchema, final TreeNode backingTreeNode) {
    super(verkleProofSchema, backingTreeNode);
  }

  public VerkleProof(
      final VerkleProofSchema schema,
      final List<SszByteVector> otherStems,
      final List<SszByte> depthExtensionPresent,
      final List<SszBytes32> commitmentsByPath,
      final SszBytes32 d,
      final IpaProof ipaProof) {
    super(
        schema,
        schema.getOtherStemsSchema().createFromElements(otherStems),
        schema.getDepthExtensionPresentSchema().createFromElements(depthExtensionPresent),
        schema.getCommitmentsByPathSchema().createFromElements(commitmentsByPath),
        d,
        ipaProof);
  }

  public VerkleProof(
      final VerkleProofSchema schema,
      final List<Bytes31> otherStems,
      final List<Byte> depthExtensionPresent,
      final List<Bytes32> commitmentsByPath,
      final Bytes32 d,
      final IpaProof ipaProof) {
    this(
        schema,
        otherStems.stream()
            .map(bytes31 -> SszByteVector.fromBytes(bytes31.getWrappedBytes()))
            .toList(),
        depthExtensionPresent.stream().map(SszByte::of).toList(),
        commitmentsByPath.stream().map(SszBytes32::of).toList(),
        SszBytes32.of(d),
        ipaProof);
  }

  public List<Bytes31> getOtherStems() {
    return getField0().stream().map(sszBytes -> new Bytes31(sszBytes.getBytes())).toList();
  }

  public List<Byte> getDepthExtensionPresent() {
    return getField1().stream().map(SszByte::get).toList();
  }

  public List<Bytes32> getCommitmentsByPath() {
    return getField2().stream().map(SszBytes32::get).toList();
  }

  public Bytes32 getD() {
    return getField3().get();
  }

  public IpaProof getIpaProof() {
    return getField4();
  }
}
