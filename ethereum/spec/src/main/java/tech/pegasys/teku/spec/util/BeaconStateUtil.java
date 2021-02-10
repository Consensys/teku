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

package tech.pegasys.teku.spec.util;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

import java.nio.ByteOrder;

import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.teku.util.config.Constants.MIN_SEED_LOOKAHEAD;

public class BeaconStateUtil {
  private final SpecConstants specConstants;

  public BeaconStateUtil(final SpecConstants specConstants) {
    this.specConstants = specConstants;
  }

  public boolean isValidGenesisState(UInt64 genesisTime, int activeValidatorCount) {
    return isItMinGenesisTimeYet(genesisTime)
        && isThereEnoughNumberOfValidators(activeValidatorCount);
  }

  private boolean isThereEnoughNumberOfValidators(int activeValidatorCount) {
    return activeValidatorCount >= specConstants.getMinGenesisActiveValidatorCount();
  }

  private boolean isItMinGenesisTimeYet(final UInt64 genesisTime) {
    return genesisTime.compareTo(specConstants.getMinGenesisTime()) >= 0;
  }

  public Bytes32 getSeed(BeaconState state, UInt64 epoch, Bytes4 domain_type)
      throws IllegalArgumentException {
    UInt64 randaoIndex = epoch.plus(specConstants.getEpochsPerHistoricalVector() - specConstants.getMinSeedLookahead() - 1);
    Bytes32 mix = getRandaoMix(state, randaoIndex);
    Bytes epochBytes = uintToBytes(epoch.longValue(), 8);
    return Hash.sha2_256(Bytes.concatenate(domain_type.getWrappedBytes(), epochBytes, mix));
  }

  public Bytes32 getRandaoMix(BeaconState state, UInt64 epoch) {
    int index = epoch.mod(specConstants.getEpochsPerHistoricalVector()).intValue();
    return state.getRandao_mixes().get(index);
  }

  public static Bytes uintToBytes(long value, int numBytes) {
    int longBytes = Long.SIZE / 8;
    Bytes valueBytes = Bytes.ofUnsignedLong(value, ByteOrder.LITTLE_ENDIAN);
    if (numBytes <= longBytes) {
      return valueBytes.slice(0, numBytes);
    } else {
      return Bytes.wrap(valueBytes, Bytes.wrap(new byte[numBytes - longBytes]));
    }
  }
}
