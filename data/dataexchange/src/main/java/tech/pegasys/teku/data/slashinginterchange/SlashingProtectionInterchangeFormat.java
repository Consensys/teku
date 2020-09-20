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

package tech.pegasys.teku.data.slashinginterchange;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;

public class SlashingProtectionInterchangeFormat {
  public final Metadata metadata;
  public final List<SigningHistory> data;

  @JsonCreator
  public SlashingProtectionInterchangeFormat(
      final Metadata metadata, final List<SigningHistory> data) {
    this.metadata = metadata;
    this.data = data;
  }
}
