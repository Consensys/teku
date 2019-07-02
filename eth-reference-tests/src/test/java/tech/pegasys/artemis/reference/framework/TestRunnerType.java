/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.reference.framework;

/*
 * The current runner types implement in v0.7.1 of the eth2.0-spec-tests.
 */

public enum TestRunnerType {
  BLS,
  EPOCH_PROCESSING,
  OPERATIONS,
  SHUFFLING,
  SSZ_GENERIC,
  SSZ_STATIC
}
