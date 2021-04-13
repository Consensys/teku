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

package tech.pegasys.teku.spec;

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;

public interface SpecFactory {
  SpecFactory PHASE0 = new Phase0SpecFactory();

  static SpecFactory getDefault() {
    return PHASE0;
  }

  Spec create(String configName);

  class Phase0SpecFactory implements SpecFactory {

    @Override
    public Spec create(String configName) {
      final SpecConfig config = SpecConfigLoader.loadConfig(configName);
      return Spec.create(config, SpecMilestone.PHASE0);
    }
  }
}
