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

package tech.pegasys.teku.validator.client.proposerconfig;

import java.io.File;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.validator.client.ProposerConfig;

public class FileProposerConfigProvider extends AbstractProposerConfigProvider {
  private final File source;

  FileProposerConfigProvider(
      final AsyncRunner asyncRunner, final boolean refresh, final File source) {
    super(asyncRunner, refresh);
    this.source = source;
  }

  @Override
  protected ProposerConfig internalGetProposerConfig() {
    return proposerConfigLoader.getProposerConfig(source);
  }
}
