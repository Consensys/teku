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

package tech.pegasys.teku.validator.client.signer;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class SigningRequestBody {
  private Bytes signingRoot;
  private final Map<String, Object> metadata = new LinkedHashMap<>();

  @SuppressWarnings("unused")
  public SigningRequestBody() {
    // keeps jackson happy
  }

  public SigningRequestBody(final Bytes signingRoot, final Map<String, Object> metadata) {
    this.signingRoot = signingRoot;
    this.metadata.putAll(metadata);
  }

  @JsonAnySetter
  public void setMetadata(final String key, final Object value) {
    metadata.put(key, value);
  }

  @JsonAnyGetter
  public Map<String, Object> getMetadata() {
    return Map.copyOf(metadata);
  }

  @JsonSetter
  public void setSigningRoot(final Bytes signingRoot) {
    this.signingRoot = signingRoot;
  }

  @JsonGetter
  public Bytes getSigningRoot() {
    return signingRoot;
  }
}
