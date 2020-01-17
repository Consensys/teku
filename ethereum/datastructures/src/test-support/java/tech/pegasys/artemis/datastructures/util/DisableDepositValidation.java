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

package tech.pegasys.artemis.datastructures.util;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class DisableDepositValidation implements BeforeAllCallback, AfterAllCallback {

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = false;
    BeaconStateUtil.DEPOSIT_PROOFS_ENABLED = false;
  }

  @Override
  public void afterAll(final ExtensionContext context) throws Exception {
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = true;
    BeaconStateUtil.DEPOSIT_PROOFS_ENABLED = true;
  }
}
