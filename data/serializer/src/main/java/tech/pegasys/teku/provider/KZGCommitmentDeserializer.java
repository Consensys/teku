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

package tech.pegasys.teku.provider;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.schema.KZGCommitment;

public class KZGCommitmentDeserializer extends JsonDeserializer<KZGCommitment> {
  @Override
  public KZGCommitment deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    return new KZGCommitment(Bytes.fromHexString(p.getValueAsString()));
  }
}
