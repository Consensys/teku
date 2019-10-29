/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.core.spec;

import org.ethereum.beacon.core.types.Gwei;

/**
 * Gwei values.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/0.4.0/specs/core/0_beacon-chain.md#gwei-values">Gwei
 *     values</a> in the spec.
 */
public interface GweiValues {

  Gwei MIN_DEPOSIT_AMOUNT = Gwei.ofEthers(1); // 1 ETH
  Gwei MAX_EFFECTIVE_BALANCE = Gwei.ofEthers(1 << 5); // 32 ETH
  Gwei EFFECTIVE_BALANCE_INCREMENT = Gwei.ofEthers(1); // 1 ETH
  Gwei EJECTION_BALANCE = Gwei.ofEthers(1 << 4); // 16 ETH

  default Gwei getMinDepositAmount() {
    return MIN_DEPOSIT_AMOUNT;
  }

  default Gwei getMaxEffectiveBalance() {
    return MAX_EFFECTIVE_BALANCE;
  }

  default Gwei getEffectiveBalanceIncrement() {
    return EFFECTIVE_BALANCE_INCREMENT;
  }

  default Gwei getEjectionBalance() {
    return EJECTION_BALANCE;
  }
}
