/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.api.schema.interfaces;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tech.pegasys.teku.api.schema.IndexedAttestation;
import tech.pegasys.teku.api.schema.electra.IndexedAttestationElectra;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
    property = "version")
@JsonSubTypes({
  @JsonSubTypes.Type(value = IndexedAttestation.class, name = "phase0"),
  @JsonSubTypes.Type(value = IndexedAttestation.class, name = "altair"),
  @JsonSubTypes.Type(value = IndexedAttestation.class, name = "bellatrix"),
  @JsonSubTypes.Type(value = IndexedAttestation.class, name = "capella"),
  @JsonSubTypes.Type(value = IndexedAttestation.class, name = "deneb"),
  @JsonSubTypes.Type(value = IndexedAttestationElectra.class, name = "electra")
})
public interface IndexedAttestationContainer {}
