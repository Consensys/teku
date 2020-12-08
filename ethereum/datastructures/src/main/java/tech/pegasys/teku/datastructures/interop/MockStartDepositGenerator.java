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

package tech.pegasys.teku.datastructures.interop;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;

import java.util.List;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.util.DepositGenerator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MockStartDepositGenerator {
  private final DepositGenerator depositGenerator;

  public MockStartDepositGenerator() {
    this(new DepositGenerator());
  }

  public MockStartDepositGenerator(DepositGenerator depositGenerator) {
    this.depositGenerator = depositGenerator;
  }

  public List<DepositData> createDeposits(final List<BLSKeyPair> validatorKeys) {
    return validatorKeys.stream().map(this::createDepositData).collect(toList());
  }

  public List<DepositData> createDeposits(
      final List<BLSKeyPair> validatorKeys, final UInt64 depositBalance) {
    return validatorKeys.stream()
        .map(key -> createDepositData(key, depositBalance))
        .collect(toList());
  }

  private DepositData createDepositData(final BLSKeyPair keyPair) {
    return depositGenerator.createDepositData(
        keyPair, MAX_EFFECTIVE_BALANCE, keyPair.getPublicKey());
  }

  private DepositData createDepositData(final BLSKeyPair keyPair, final UInt64 depositBalance) {
    return depositGenerator.createDepositData(keyPair, depositBalance, keyPair.getPublicKey());
  }
}
