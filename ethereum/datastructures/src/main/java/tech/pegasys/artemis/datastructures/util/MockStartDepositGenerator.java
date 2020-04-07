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

package tech.pegasys.artemis.datastructures.util;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.artemis.util.config.Constants.MAX_EFFECTIVE_BALANCE;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.bls.bls.BLSKeyPair;

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

  private DepositData createDepositData(final BLSKeyPair keyPair) {
    return depositGenerator.createDepositData(
        keyPair, UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE), keyPair.getPublicKey());
  }
}
