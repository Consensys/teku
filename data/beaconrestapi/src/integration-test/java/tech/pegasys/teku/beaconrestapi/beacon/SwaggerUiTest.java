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

package tech.pegasys.teku.beaconrestapi.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;

public class SwaggerUiTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldShowSwaggerInterface() throws IOException {
    Set<String> links = findLinks(getUrl("/swagger-ui"));

    links.forEach(this::checkPath);
  }

  private void checkPath(String path) {
    try {
      Response response = getResponse(path);
      assertThat(response.code()).as("Check deep link (%s)", path).isEqualTo(200);
    } catch (IOException exception) {
      fail("failed to fetch resource");
    }
  }

  private static Set<String> findLinks(String url) throws IOException {

    Set<String> links = new HashSet<>();

    Document doc =
        Jsoup.connect(url).data("query", "Java").userAgent("Mozilla").timeout(3000).get();

    Elements elements = doc.select("link[href]");
    for (Element element : elements) {
      links.add(element.attr("href"));
    }

    return links;
  }
}
