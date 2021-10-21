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

package tech.pegasys.teku.api.schema;

import tech.pegasys.teku.spec.SpecMilestone;

public enum Version {
  phase0,
  altair,
  merge;

  public static Version fromMilestone(final SpecMilestone milestone) {
    switch (milestone) {
      case MERGE:
        return merge;
      case ALTAIR:
        return altair;
      case PHASE0:
        return phase0;
      default:
        throw new UnsupportedOperationException(
            "Milestone " + milestone.name() + "was not found in Schema Version for api.");
    }
  }
}
