/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.validator.client.signer;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class SigningRequestBody {
  @JsonProperty("signingRoot")
  private Bytes signingRoot;

  @JsonProperty("type")
  private SignType type;

  @JsonAnySetter private final Map<String, Object> metadata = new HashMap<>();

  public SigningRequestBody() {
    // keeps jackson happy
  }

  public SigningRequestBody(
      final Bytes signingRoot, final SignType type, final Map<String, Object> metadata) {
    this.signingRoot = signingRoot;
    this.type = type;
    this.metadata.putAll(metadata);
  }

  @JsonAnyGetter
  public Map<String, Object> getMetadata() {
    return Collections.unmodifiableMap(metadata);
  }

  public void setSigningRoot(final Bytes signingRoot) {
    this.signingRoot = signingRoot;
  }

  public Bytes getSigningRoot() {
    return signingRoot;
  }

  public SignType getType() {
    return type;
  }

  public void setType(final SignType type) {
    this.type = type;
  }
}
