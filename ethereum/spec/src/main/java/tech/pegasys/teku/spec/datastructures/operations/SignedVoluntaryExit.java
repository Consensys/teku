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

package tech.pegasys.teku.spec.datastructures.operations;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class SignedVoluntaryExit
    extends Container2<SignedVoluntaryExit, VoluntaryExit, SszSignature> {

  public static class SignedVoluntaryExitSchema
      extends ContainerSchema2<SignedVoluntaryExit, VoluntaryExit, SszSignature> {

    public SignedVoluntaryExitSchema() {
      super(
          "SignedVoluntaryExit",
          namedSchema("message", VoluntaryExit.SSZ_SCHEMA),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    @Override
    public SignedVoluntaryExit createFromBackingNode(TreeNode node) {
      return new SignedVoluntaryExit(this, node);
    }
  }

  public static final SignedVoluntaryExitSchema SSZ_SCHEMA = new SignedVoluntaryExitSchema();

  private SignedVoluntaryExit(SignedVoluntaryExitSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedVoluntaryExit(final VoluntaryExit message, final BLSSignature signature) {
    super(SSZ_SCHEMA, message, new SszSignature(signature));
  }

  public VoluntaryExit getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", getMessage())
        .add("signature", getSignature())
        .toString();
  }
}
