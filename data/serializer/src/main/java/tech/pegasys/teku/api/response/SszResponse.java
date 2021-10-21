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

package tech.pegasys.teku.api.response;

import java.io.ByteArrayInputStream;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.spec.SpecMilestone;

public class SszResponse {
  public final ByteArrayInputStream byteStream;
  public final Version version;
  public final String abbreviatedHash;

  public SszResponse(
      final ByteArrayInputStream byteStream,
      final String abbreviatedHash,
      final SpecMilestone specMilestone) {
    this.byteStream = byteStream;
    this.abbreviatedHash = abbreviatedHash;
    this.version = Version.fromMilestone(specMilestone);
  }
}
