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
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class SignedVoluntaryExit
    extends Container2<SignedVoluntaryExit, VoluntaryExit, SszVector<SszByte>> {

  public static class SignedVoluntaryExitSchema
      extends ContainerSchema2<SignedVoluntaryExit, VoluntaryExit, SszVector<SszByte>> {

    public SignedVoluntaryExitSchema() {
      super(
          "SignedVoluntaryExit",
          namedSchema("message", VoluntaryExit.SSZ_SCHEMA),
          namedSchema("signature", SszComplexSchemas.BYTES_96_SCHEMA));
    }

    @Override
    public SignedVoluntaryExit createFromBackingNode(TreeNode node) {
      return new SignedVoluntaryExit(this, node);
    }
  }

  public static final SignedVoluntaryExitSchema SSZ_SCHEMA = new SignedVoluntaryExitSchema();

  private BLSSignature signatureCache;

  private SignedVoluntaryExit(SignedVoluntaryExitSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedVoluntaryExit(final VoluntaryExit message, final BLSSignature signature) {
    super(SSZ_SCHEMA, message, SszUtils.toSszByteVector(signature.toBytesCompressed()));
    this.signatureCache = signature;
  }

  public VoluntaryExit getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(SszUtils.getAllBytes(getField1()));
    }
    return signatureCache;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", getMessage())
        .add("signature", getSignature())
        .toString();
  }
}
