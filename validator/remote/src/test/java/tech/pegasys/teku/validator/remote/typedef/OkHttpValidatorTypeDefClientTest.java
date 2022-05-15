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

package tech.pegasys.teku.validator.remote.typedef;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;

import java.util.Optional;

class OkHttpValidatorTypeDefClientTest {
  private final OkHttpClient okHttpClient = new OkHttpClient();
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  final OkHttpValidatorTypeDefClient client =
      new OkHttpValidatorTypeDefClient(okHttpClient, HttpUrl.parse("http://localhost:5051"), spec);

  @Test
  void test() {
    final Optional<GenesisData> maybeGenesisData = client.getGenesis();
    assertThat(maybeGenesisData).isNotEmpty();
  }

  @Test
  void t2() {
    MediaType mediaType = MediaType.parse("application/octet-stream");
    assertThat(mediaType.type()).isEqualTo("application");
    assertThat(mediaType.subtype()).isEqualTo("octet-stream");
  }
}
