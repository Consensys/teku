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

package org.ethereum.beacon.core;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.ProposerSlashing;
import org.ethereum.beacon.core.operations.Transfer;
import org.ethereum.beacon.core.operations.VoluntaryExit;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.operations.attestation.AttestationDataAndCustodyBit;
import org.ethereum.beacon.core.operations.attestation.Crosslink;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.operations.slashing.IndexedAttestation;
import org.ethereum.beacon.core.state.BeaconStateImpl;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.core.state.CompactCommittee;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.state.Fork;
import org.ethereum.beacon.core.state.HistoricalBatch;
import org.ethereum.beacon.core.state.PendingAttestation;
import org.ethereum.beacon.core.state.ValidatorRecord;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.Millis;
import org.ethereum.beacon.core.types.ShardNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.collections.Bitlist;
import tech.pegasys.artemis.util.collections.Bitvector;

/**
 * Verifies {@link SSZSerializable} model test coverage
 *
 * <p>Check {@link #testAnnotatedClassesHaveTests()} JavaDoc for more info
 */
public class SSZSerializableAnnotationTest {

  /**
   * Scans all classes accessible from the context class loader which belong to the given package
   * and subpackages.
   *
   * @param packageName The base package
   * @return The classes
   * @throws ClassNotFoundException
   * @throws IOException
   */
  private static Class[] getClasses(String packageName) throws ClassNotFoundException, IOException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assert classLoader != null;
    String path = packageName.replace('.', '/');
    Enumeration<URL> resources = classLoader.getResources(path);
    List<File> dirs = new ArrayList<File>();
    while (resources.hasMoreElements()) {
      URL resource = resources.nextElement();
      dirs.add(new File(resource.getFile()));
    }
    ArrayList<Class> classes = new ArrayList<Class>();
    for (File directory : dirs) {
      classes.addAll(findClasses(directory, packageName));
    }
    return classes.toArray(new Class[classes.size()]);
  }

  /**
   * Recursive method used to find all classes in a given directory and subdirs.
   *
   * @param directory The base directory
   * @param packageName The package name for classes found inside the base directory
   * @return The classes
   * @throws ClassNotFoundException
   */
  private static List<Class> findClasses(File directory, String packageName)
      throws ClassNotFoundException {
    List<Class> classes = new ArrayList<Class>();
    if (!directory.exists()) {
      return classes;
    }
    File[] files = directory.listFiles();
    for (File file : files) {
      if (file.isDirectory()) {
        assert !file.getName().contains(".");
        classes.addAll(findClasses(file, packageName + "." + file.getName()));
      } else if (file.getName().endsWith(".class")) {
        classes.add(
            Class.forName(
                packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
      }
    }
    return classes;
  }

  /**
   * Just add your {@link SSZSerializable} class to the list to stop this test fail.
   *
   * <p>This test notifies user that every {@link SSZSerializable} model should be tested like in
   * {@link ModelsSerializeTest} to clarify in runtime correctness of annotation scheme building for
   * each case and test other routines
   */
  @Test
  public void testAnnotatedClassesHaveTests() throws Exception {
    Set<Class> testedClasses =
        new HashSet<>(
            Arrays.asList(
                Attestation.class,
                AttestationData.class,
                AttestationDataAndCustodyBit.class,
                AttesterSlashing.class,
                BeaconBlock.class,
                BeaconBlockBody.class,
                BeaconStateImpl.class,
                Deposit.class,
                DepositData.class,
                VoluntaryExit.class,
                ProposerSlashing.class,
                Crosslink.class,
                Fork.class,
                PendingAttestation.class,
                ValidatorRecord.class,
                Eth1Data.class,
                Bitvector.class,
                Bitlist.class,
                BLSPubkey.class,
                BLSSignature.class,
                EpochNumber.class,
                Gwei.class,
                IndexedAttestation.class,
                ShardNumber.class,
                SlotNumber.class,
                Time.class,
                Millis.class,
                ValidatorIndex.class,
                Transfer.class,
                BeaconBlockHeader.class,
                HistoricalBatch.class,
                Checkpoint.class,
                CompactCommittee.class));
    Class[] allClasses = getClasses("org.ethereum.beacon.core");

    for (Class clazz : allClasses) {
      if (testedClasses.contains(clazz)) continue;

      if (clazz.isAnnotationPresent(SSZSerializable.class) && clazz.getEnclosingClass() == null) {
        throw new RuntimeException(
            String.format(
                "Class %s is marked with "
                    + "@SSZSerializable annotation but not covered with tests!",
                clazz.getName()));
      }
    }
  }
}
