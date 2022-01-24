/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.cli.subcommand.debug;

import java.nio.file.Path;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.validator.client.KeyManager;

public class NoOpKeyManager extends KeyManager {
  NoOpKeyManager() {
    super(null, null);
  }

  @Override
  public DataDirLayout getDataDirLayout() {
    return new DataDirLayout() {
      @Override
      public Path getBeaconDataDirectory() {
        return Path.of(".");
      }

      @Override
      public Path getValidatorDataDirectory() {
        return Path.of(".");
      }
    };
  }
}
