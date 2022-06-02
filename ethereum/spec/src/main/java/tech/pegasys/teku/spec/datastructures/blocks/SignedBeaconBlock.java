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

import static com.google.common.base.Preconditions.checkState;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class SignedBeaconBlock extends Container2<SignedBeaconBlock, BeaconBlock, SszSignature>
    implements BeaconBlockSummary {

  SignedBeaconBlock(SignedBeaconBlockSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  SignedBeaconBlock(
      final SignedBeaconBlockSchema type, final BeaconBlock message, final BLSSignature signature) {
    super(type, message, new SszSignature(signature));
  }

  public static SignedBeaconBlock create(
      final Spec spec, final BeaconBlock message, final BLSSignature signature) {
    SignedBeaconBlockSchema signedBeaconBlockSchema =
        message.getBody().isBlinded()
            ? spec.atSlot(message.getSlot())
                .getSchemaDefinitions()
                .getSignedBlindedBeaconBlockSchema()
            : spec.atSlot(message.getSlot()).getSchemaDefinitions().getSignedBeaconBlockSchema();
    return new SignedBeaconBlock(signedBeaconBlockSchema, message, signature);
  }

  public SignedBeaconBlock blind(final SchemaDefinitions schemaDefinitions) {
    if (getMessage().getBody().isBlinded()) {
      return this;
    }

    final TreeNode blindedTree = getBlindedTree();
    return schemaDefinitions.getSignedBlindedBeaconBlockSchema().createFromBackingNode(blindedTree);
  }

  private TreeNode getBlindedTree() {
    final SignedBeaconBlockSchema schema = getSchema();
    final Optional<Long> maybeNodeIndexToBlind = schema.getBlindedNodeGeneralizedIndex();
    final TreeNode backingNode = getBackingNode();
    if (maybeNodeIndexToBlind.isEmpty()) {
      return backingNode;
    }
    final long nodeIndexToBlind = maybeNodeIndexToBlind.get();
    final Bytes32 blindedNodeRoot = backingNode.get(nodeIndexToBlind).hashTreeRoot();
    final TreeNode blindedNode = SszBytes32.of(blindedNodeRoot).getBackingNode();
    return backingNode.updated(nodeIndexToBlind, blindedNode);
  }

  public SignedBeaconBlock unblind(
      final SchemaDefinitions schemaDefinitions, final ExecutionPayload payload) {
    if (!getMessage().getBody().isBlinded()) {
      return this;
    }

    final TreeNode unblindedTree = getUnblindedTree(payload);
    return schemaDefinitions.getSignedBeaconBlockSchema().createFromBackingNode(unblindedTree);
  }

  private TreeNode getUnblindedTree(final ExecutionPayload payload) {
    final SignedBeaconBlockSchema schema = getSchema();
    final Optional<Long> maybeBlindedNodeIndex = schema.getBlindedNodeGeneralizedIndex();
    final TreeNode backingNode = getBackingNode();
    if (maybeBlindedNodeIndex.isEmpty()) {
      return backingNode;
    }
    final long blindedNodeIndex = maybeBlindedNodeIndex.get();
    final Bytes32 expectedRoot = backingNode.get(blindedNodeIndex).hashTreeRoot();
    final TreeNode replacement = payload.getUnblindedNode();
    checkState(
        expectedRoot.equals(replacement.hashTreeRoot()),
        "Root in blinded block does not match provided replacement root");
    return backingNode.updated(blindedNodeIndex, replacement);
  }

  @Override
  public SignedBeaconBlockSchema getSchema() {
    return (SignedBeaconBlockSchema) super.getSchema();
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
