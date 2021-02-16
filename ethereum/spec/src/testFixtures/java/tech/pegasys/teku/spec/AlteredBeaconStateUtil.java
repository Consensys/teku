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

package tech.pegasys.teku.spec;

import java.util.Map;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.spec.util.CommitteeUtil;

public class AlteredBeaconStateUtil extends BeaconStateUtil {

  /**
   * For debug/test purposes only enables/disables {@link DepositData} BLS signature verification
   * Setting to <code>false</code> significantly speeds up state initialization
   */
  private final boolean verifyDeposits;

  public AlteredBeaconStateUtil(
      final SpecConstants specConstants,
      final CommitteeUtil committeeUtil,
      final boolean verifyDeposits) {
    super(specConstants, committeeUtil);
    this.verifyDeposits = verifyDeposits;
  }

  @Override
  protected boolean verifyDeposits(
      final BLSPublicKey pubkey,
      final Deposit deposit,
      final UInt64 amount,
      final Map<BLSPublicKey, Integer> pubKeyToIndexMap) {
    if (verifyDeposits) {
      return super.verifyDeposits(pubkey, deposit, amount, pubKeyToIndexMap);
    }
    return true;
  }
}
