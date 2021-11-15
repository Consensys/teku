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

package tech.pegasys.teku.cli.options;

import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.storage.store.StoreConfig;

public class StoreOptions {
  @Option(
      hidden = true,
      names = {"--Xhot-state-persistence-frequency"},
      paramLabel = "<INTEGER>",
      description =
          "How frequently to persist hot states in epochs.  A value less than or equal to zero disables hot state persistence.",
      arity = "1")
  private int hotStatePersistenceFrequencyInEpochs =
      StoreConfig.DEFAULT_HOT_STATE_PERSISTENCE_FREQUENCY_IN_EPOCHS;

  public void configure(final TekuConfiguration.Builder builder) {
    builder.store(
        b -> b.hotStatePersistenceFrequencyInEpochs(hotStatePersistenceFrequencyInEpochs));
  }
}
