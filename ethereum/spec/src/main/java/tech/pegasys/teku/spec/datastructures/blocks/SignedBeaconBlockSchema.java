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

package tech.pegasys.teku.spec.datastructures.blocks;

import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedBeaconBlockSchema
    extends ContainerSchema2<SignedBeaconBlock, BeaconBlock, SszSignature> {

  public SignedBeaconBlockSchema(
      final BeaconBlockSchema beaconBlockSchema, final String containerName) {
    super(
        containerName,
        namedSchema(SignedBeaconBlockFields.MESSAGE, beaconBlockSchema),
        namedSchema(SignedBeaconBlockFields.SIGNATURE, SszSignatureSchema.INSTANCE));
  }

  public SignedBeaconBlock create(final BeaconBlock message, final BLSSignature signature) {
    return new SignedBeaconBlock(this, message, signature);
  }

  @Override
  public SignedBeaconBlock createFromBackingNode(TreeNode node) {
    return new SignedBeaconBlock(this, node);
  }

  public Optional<Long> getBlindedNodeGeneralizedIndex() {
    return getBlockSchema()
        .getBlindedNodeGeneralizedIndex()
        .map(
            relativeIndex -> {
              final long childGeneralizedIndex =
                  getChildGeneralizedIndex(getFieldIndex(SignedBeaconBlockFields.MESSAGE));
              return GIndexUtil.gIdxCompose(childGeneralizedIndex, relativeIndex);
            });
  }

  public BeaconBlockSchema getBlockSchema() {
    return (BeaconBlockSchema) getChildSchema(getFieldIndex(SignedBeaconBlockFields.MESSAGE));
  }
}
