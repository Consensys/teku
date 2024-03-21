/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.util;

import java.security.SecureRandom;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class DepositReceiptsUtil {

  @SuppressWarnings("DoNotCreateSecureRandomDirectly")
  private final SecureRandom random = new SecureRandom();

  private final Spec spec;

  public DepositReceiptsUtil(final Spec spec) {
    this.spec = spec;
  }

  public DepositReceipt createDepositReceipt(final UInt64 slot, final UInt64 index) {
    final BLSKeyPair validatorKeyPair = BLSKeyPair.random(random);
    final BLSPublicKey publicKey = validatorKeyPair.getPublicKey();
    final UInt64 depositAmount = UInt64.THIRTY_TWO_ETH;
    final DepositMessage depositMessage =
        new DepositMessage(publicKey, Bytes32.ZERO, depositAmount);
    final MiscHelpers miscHelpers = spec.atSlot(slot).miscHelpers();
    final Bytes32 depositDomain = miscHelpers.computeDomain(Domain.DEPOSIT);
    final BLSSignature signature =
        BLS.sign(
            validatorKeyPair.getSecretKey(),
            miscHelpers.computeSigningRoot(depositMessage, depositDomain));
    return DepositReceipt.SSZ_SCHEMA.create(
        publicKey, Bytes32.ZERO, depositAmount, signature, index);
  }
}
