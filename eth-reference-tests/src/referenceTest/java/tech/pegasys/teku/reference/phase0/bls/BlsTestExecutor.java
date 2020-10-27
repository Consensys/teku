/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.reference.phase0.bls;

import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.impl.blst.BlstBLS12381;
import tech.pegasys.teku.bls.impl.mikuli.MikuliBLS12381;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public abstract class BlsTestExecutor implements TestExecutor {

  @Override
  public final void runTest(TestDefinition testDefinition) throws Throwable {
    if (BlstBLS12381.INSTANCE.isPresent()) {
      try {
        BLS.setBlsImplementation(BlstBLS12381.INSTANCE.get());
        runTestImpl(testDefinition);
      } finally {
        BLS.resetBlsImplementation();
      }
    }

    try {
      BLS.setBlsImplementation(MikuliBLS12381.INSTANCE);
      runTestImpl(testDefinition);
    } finally {
      BLS.resetBlsImplementation();
    }
  }

  protected abstract void runTestImpl(TestDefinition testDefinition) throws Throwable;
}
