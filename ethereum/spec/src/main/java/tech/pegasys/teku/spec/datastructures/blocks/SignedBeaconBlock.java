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

package tech.pegasys.teku.spec.datastructures.blocks;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.ssz.containers.Container2;
import tech.pegasys.teku.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.util.config.SpecDependent;

public class SignedBeaconBlock extends Container2<SignedBeaconBlock, BeaconBlock, SszSignature>
    implements BeaconBlockSummary {

  public static class SignedBeaconBlockSchema
      extends ContainerSchema2<SignedBeaconBlock, BeaconBlock, SszSignature> {

    public SignedBeaconBlockSchema() {
      super(
          "SignedBeaconBlock",
          namedSchema("message", BeaconBlock.SSZ_SCHEMA.get()),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    @Override
    public SignedBeaconBlock createFromBackingNode(TreeNode node) {
      return new SignedBeaconBlock(this, node);
    }
  }

  public static SignedBeaconBlockSchema getSszSchema() {
    return SSZ_SCHEMA.get();
  }

  public static final SpecDependent<SignedBeaconBlockSchema> SSZ_SCHEMA =
      SpecDependent.of(SignedBeaconBlockSchema::new);

  private SignedBeaconBlock(SignedBeaconBlockSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Deprecated
  public SignedBeaconBlock(final BeaconBlock message, final BLSSignature signature) {
    this(SSZ_SCHEMA.get(), message, signature);
  }

  public SignedBeaconBlock(
      final SignedBeaconBlockSchema type, final BeaconBlock message, final BLSSignature signature) {
    super(type, message, new SszSignature(signature));
  }

  public BeaconBlock getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
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
