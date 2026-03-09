/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.logging;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import org.apache.tuweni.units.bigints.UInt256;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Converter {

  public static String weiToEth(final UInt256 wei) {
    final BigDecimal eth = Convert.fromWei(wei.toDecimalString(), Convert.Unit.ETHER);
    return eth.setScale(6, RoundingMode.HALF_UP).toString();
  }

  public static String gweiToEth(final UInt64 gwei) {
    final BigDecimal wei = Convert.toWei(gwei.toString(), Unit.GWEI);
    return weiToEth(UInt256.valueOf(wei.toBigInteger()));
  }

  public static UInt64 weiToGwei(final UInt256 wei) {
    final BigInteger gwei = Convert.fromWei(wei.toDecimalString(), Unit.GWEI).toBigInteger();
    return UInt64.valueOf(gwei);
  }
}
