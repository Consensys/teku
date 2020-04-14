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

package tech.pegasys.artemis.util.config;

/**
 * Defines feature toggles to enable or disable new functionality which may not be fully ready yet.
 * Allows larger pieces of work to be delivered in smaller PRs without breaking functionality while
 * they are under development.
 */
public class FeatureToggles {

  /**
   * Controls whether the ValidatorClientService is used to generate blocks and attestations or if
   * ValidatorCoordinator performs those duties.
   */
  public static final boolean USE_VALIDATOR_CLIENT_SERVICE = true;
}
