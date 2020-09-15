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

package tech.pegasys.teku.config;

import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class TekuConfiguration {
  private final GlobalConfiguration globalConfiguration;
  private final WeakSubjectivityConfig weakSubjectivityConfig;

  TekuConfiguration(
      GlobalConfiguration globalConfiguration, WeakSubjectivityConfig weakSubjectivityConfig) {
    this.globalConfiguration = globalConfiguration;
    this.weakSubjectivityConfig = weakSubjectivityConfig;
  }

  public static TekuConfigurationBuilder builder() {
    return new TekuConfigurationBuilder();
  }

  public GlobalConfiguration global() {
    return globalConfiguration;
  }

  public WeakSubjectivityConfig weakSubjectivity() {
    return weakSubjectivityConfig;
  }
}
