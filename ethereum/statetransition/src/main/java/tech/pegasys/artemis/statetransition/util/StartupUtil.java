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

package tech.pegasys.artemis.statetransition.util;

import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateImpl;
import tech.pegasys.artemis.datastructures.util.DepositGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartBeaconStateGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartDepositGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.storage.client.RecentChainData;

public final class StartupUtil {

  public static final Logger LOG = LogManager.getLogger();

  public static BeaconState createMockedStartInitialBeaconState(
      final long genesisTime, List<BLSKeyPair> validatorKeys) {
    return createMockedStartInitialBeaconState(genesisTime, validatorKeys, true);
  }

  public static BeaconState createMockedStartInitialBeaconState(
      final long genesisTime, List<BLSKeyPair> validatorKeys, boolean signDeposits) {
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(new DepositGenerator(signDeposits))
            .createDeposits(validatorKeys);
    return new MockStartBeaconStateGenerator()
        .createInitialBeaconState(UnsignedLong.valueOf(genesisTime), initialDepositData);
  }

  public static BeaconState loadBeaconStateFromFile(final String stateFile) throws IOException {
    return SimpleOffsetSerializer.deserialize(
        Bytes.wrap(Files.readAllBytes(new File(stateFile).toPath())), BeaconStateImpl.class);
  }

  public static void setupInitialState(
      final RecentChainData recentChainData,
      final long genesisTime,
      final String startState,
      final int numValidators) {
    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, numValidators);
    setupInitialState(recentChainData, genesisTime, startState, validatorKeys, true);
  }

  public static void setupInitialState(
      final RecentChainData recentChainData,
      final long genesisTime,
      final String startState,
      final List<BLSKeyPair> validatorKeyPairs,
      final boolean signDeposits) {
    BeaconState initialState;
    if (startState != null) {
      try {
        LOG.log(Level.INFO, "Loading initial state from " + startState);
        initialState = StartupUtil.loadBeaconStateFromFile(startState);
      } catch (final IOException e) {
        throw new IllegalStateException("Failed to load initial state", e);
      }
    } else {
      LOG.log(
          Level.INFO,
          "Starting with mocked start interoperability mode with genesis time "
              + genesisTime
              + " and "
              + validatorKeyPairs.size()
              + " validators");
      initialState =
          StartupUtil.createMockedStartInitialBeaconState(
              genesisTime, validatorKeyPairs, signDeposits);
    }

    recentChainData.initializeFromGenesis(initialState);
  }
}
