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

import tech.pegasys.artemis.ethereum.core.Address;

/**
 * Deposit contract constants.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#deposit-contract">Deposit
 *     contract</a> in the spec
 */
public interface DepositContractParameters {

  Address DEPOSIT_CONTRACT_ADDRESS =
      Address.fromHexString("0x0000000000000000000000000000000000000000"); // TBD

  /* Values defined in the spec. */

  default Address getDepositContractAddress() {
    return DEPOSIT_CONTRACT_ADDRESS;
  }
}
