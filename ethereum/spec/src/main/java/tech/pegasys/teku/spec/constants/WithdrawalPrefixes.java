/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.constants;

import org.apache.tuweni.bytes.Bytes;

public class WithdrawalPrefixes {
  /*
   * <spec constant_var="BLS_WITHDRAWAL_PREFIX" fork="phase0">
   * BLS_WITHDRAWAL_PREFIX: Bytes1 = '0x00'
   * </spec>
   */
  public static final Bytes BLS_WITHDRAWAL_PREFIX = Bytes.fromHexString("0x00");

  /*
   * <spec constant_var="ETH1_ADDRESS_WITHDRAWAL_PREFIX" fork="phase0">
   * ETH1_ADDRESS_WITHDRAWAL_PREFIX: Bytes1 = '0x01'
   * </spec>
   */
  public static final byte ETH1_ADDRESS_WITHDRAWAL_BYTE = 0x01;

  /*
   * <spec constant_var="COMPOUNDING_WITHDRAWAL_PREFIX" fork="electra">
   * COMPOUNDING_WITHDRAWAL_PREFIX: Bytes1 = '0x02'
   * </spec>
   */
  public static final byte COMPOUNDING_WITHDRAWAL_BYTE = 0x02;

  /*
   * <spec constant_var="ETH1_ADDRESS_WITHDRAWAL_PREFIX" fork="electra">
   * ETH1_ADDRESS_WITHDRAWAL_PREFIX: Bytes1 = '0x01'
   * </spec>
   */
  public static final Bytes ETH1_ADDRESS_WITHDRAWAL_PREFIX = Bytes.of(ETH1_ADDRESS_WITHDRAWAL_BYTE);
}
