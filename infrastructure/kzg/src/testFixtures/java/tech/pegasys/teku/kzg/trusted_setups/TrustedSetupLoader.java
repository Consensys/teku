/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.kzg.trusted_setups;

import com.google.common.io.Resources;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGException;

/** Loads trusted setup used in tests throughout the project. */
public class TrustedSetupLoader {

  public static final String TEST_TRUSTED_SETUP = "trusted_setup.txt";

  public static void loadTrustedSetupForTests(final KZG kzg) throws KZGException {
    kzg.loadTrustedSetup(getTrustedSetupFile(TEST_TRUSTED_SETUP), 0);
  }

  public static String getTrustedSetupFile(final String filename) {
    return Resources.getResource(TrustedSetupLoader.class, filename).toExternalForm();
  }
}
