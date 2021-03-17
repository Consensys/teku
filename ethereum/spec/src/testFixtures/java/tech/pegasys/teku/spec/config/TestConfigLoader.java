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

package tech.pegasys.teku.spec.config;

import static tech.pegasys.teku.spec.config.SpecConfigLoader.processConstants;

import java.util.function.Consumer;

public class TestConfigLoader {
  public static SpecConfig loadConstants(
      final String constants, final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfigReader reader = new SpecConfigReader();
    processConstants(constants, reader::read);
    return reader.build(modifier);
  }
}
