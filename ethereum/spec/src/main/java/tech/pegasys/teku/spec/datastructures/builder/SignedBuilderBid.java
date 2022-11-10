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

package tech.pegasys.teku.spec.datastructures.builder;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SignedBuilderBid extends Container2<SignedBuilderBid, BuilderBid, SszSignature>
    implements SszContainer {

  SignedBuilderBid(SignedBuilderBidSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  SignedBuilderBid(
      final SignedBuilderBidSchema type, final BuilderBid message, final BLSSignature signature) {
    super(type, message, new SszSignature(signature));
  }

  @Override
  public SignedBuilderBidSchema getSchema() {
    return (SignedBuilderBidSchema) super.getSchema();
  }

  public BuilderBid getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }
}
