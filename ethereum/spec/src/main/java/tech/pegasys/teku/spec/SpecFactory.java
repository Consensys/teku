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

import java.util.List;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.state.Fork;

public interface SpecFactory {
  SpecFactory PHASE0 = new Phase0SpecFactory();
  SpecFactory MERGE_FROM_GENESIS = new MergeFromGenesisSpecFactory();

  static SpecFactory getDefault() {
    return PHASE0;
  }

  static SpecFactory getMergeFromGenesis() {
    return MERGE_FROM_GENESIS;
  }

  default Spec create(String configName) {
    return create(configName, null);
  }

  Spec create(String configName, String eth1Endpoint);

  class MergeFromGenesisSpecFactory implements SpecFactory {

    @Override
    public Spec create(String configName, String eth1Endpoint) {
      final SpecConfigMerge config =
          SpecConfigMerge.required(SpecConfigLoader.loadConfig(configName));
      final ForkManifest forkManifest =
          ForkManifest.create(
              List.of(
                  new Fork(
                      config.getGenesisForkVersion(),
                      config.getGenesisForkVersion(),
                      SpecConfig.GENESIS_EPOCH)));
      return Spec.createWithMergeFromGenesis(config, forkManifest, eth1Endpoint);
    }
  }

  class Phase0SpecFactory implements SpecFactory {

    @Override
    public Spec create(String configName, String eth1Endpoint) {
      final SpecConfig config = SpecConfigLoader.loadConfig(configName);
      final ForkManifest forkManifest =
          ForkManifest.create(
              List.of(
                  new Fork(
                      config.getGenesisForkVersion(),
                      config.getGenesisForkVersion(),
                      SpecConfig.GENESIS_EPOCH)));
      return Spec.create(config, forkManifest, eth1Endpoint);
    }
  }
}
