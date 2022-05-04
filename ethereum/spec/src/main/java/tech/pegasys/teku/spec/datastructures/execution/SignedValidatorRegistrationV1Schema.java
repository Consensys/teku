/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.execution;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedValidatorRegistrationV1Schema
    extends ContainerSchema2<SignedValidatorRegistrationV1, ValidatorRegistrationV1, SszSignature> {

  public SignedValidatorRegistrationV1Schema(
      final ValidatorRegistrationV1Schema validatorRegistrationV1Schema) {
    super(
        "SignedValidatorRegistrationV1",
        namedSchema("message", validatorRegistrationV1Schema),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public SignedValidatorRegistrationV1 create(
      final ValidatorRegistrationV1 message, final BLSSignature signature) {
    return new SignedValidatorRegistrationV1(this, message, signature);
  }

  @Override
  public SignedValidatorRegistrationV1 createFromBackingNode(TreeNode node) {
    return new SignedValidatorRegistrationV1(this, node);
  }
}
