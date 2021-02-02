/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.blocks;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.util.SpecDependent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;

public class SignedBeaconBlock
    extends Container2<SignedBeaconBlock, BeaconBlock, VectorViewRead<ByteView>>
    implements BeaconBlockSummary {

  public static class SignedBeaconBlockType
      extends ContainerType2<SignedBeaconBlock, BeaconBlock, VectorViewRead<ByteView>> {

    public SignedBeaconBlockType() {
      super(
          "SignedBeaconBlock",
          namedType("message", BeaconBlock.TYPE.get()),
          namedType("signature", ComplexViewTypes.BYTES_96_TYPE));
    }

    @Override
    public SignedBeaconBlock createFromBackingNode(TreeNode node) {
      return new SignedBeaconBlock(this, node);
    }
  }

  public static SignedBeaconBlockType getSszType() {
    return TYPE.get();
  }

  public static final SpecDependent<SignedBeaconBlockType> TYPE =
      SpecDependent.of(SignedBeaconBlockType::new);

  private BLSSignature signatureCache;

  private SignedBeaconBlock(SignedBeaconBlockType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Deprecated
  public SignedBeaconBlock(final BeaconBlock message, final BLSSignature signature) {
    this(TYPE.get(), message, signature);
  }

  public SignedBeaconBlock(
      final SignedBeaconBlockType type, final BeaconBlock message, final BLSSignature signature) {
    super(type, message, ViewUtils.createVectorFromBytes(signature.toBytesCompressed()));
    this.signatureCache = signature;
  }

  public BeaconBlock getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField1()));
    }
    return signatureCache;
  }

  @Override
  public UInt64 getSlot() {
    return getMessage().getSlot();
  }

  @Override
  public Bytes32 getParentRoot() {
    return getMessage().getParentRoot();
  }

  @Override
  public UInt64 getProposerIndex() {
    return getMessage().getProposerIndex();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return getMessage().getBodyRoot();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return Optional.of(getMessage());
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBeaconBlock() {
    return Optional.of(this);
  }

  /**
   * Get the state root of the BeaconBlock that is being signed.
   *
   * @return The hashed tree root of the {@code BeaconBlock} being signed.
   */
  @Override
  public Bytes32 getStateRoot() {
    return getMessage().getStateRoot();
  }

  /**
   * Get the root of the BeaconBlock that is being signed.
   *
   * @return The hashed tree root of the {@code BeaconBlock} being signed.
   */
  @Override
  public Bytes32 getRoot() {
    return getMessage().hashTreeRoot();
  }
}
