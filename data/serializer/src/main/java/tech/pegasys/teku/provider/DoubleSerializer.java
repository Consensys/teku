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

package tech.pegasys.teku.provider;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class DoubleSerializer extends JsonSerializer<Double> {
  private static final DecimalFormatSymbols US_SYMBOLS = new DecimalFormatSymbols(Locale.US);

  @Override
  public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeString(formatValue(value));
  }

  private String formatValue(Double value) {
    DecimalFormat df = new DecimalFormat("#.####", US_SYMBOLS);
    df.setRoundingMode(RoundingMode.HALF_UP);
    return df.format(value);
  }
}
