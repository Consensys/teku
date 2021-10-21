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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import org.apache.commons.lang3.StringUtils;

public class TestServletOutputStream extends ServletOutputStream {
  private StringBuilder builder = new StringBuilder();
  private int writeCounter = 0;

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public void setWriteListener(final WriteListener writeListener) {}

  @Override
  public void write(final int i) {
    writeCounter++;
    builder.append((char) i);
  }

  public String getString() {
    return builder.toString();
  }

  public int getWriteCounter() {
    return writeCounter;
  }

  public int countEvents() {
    return StringUtils.countMatches(getString(), "event: ");
  }

  public List<String> getEvents() {
    final List<String> result = new ArrayList<>();
    String[] splits = StringUtils.splitByWholeSeparator(getString(), "event: ");
    for (String s : splits) {
      result.add(String.format("event: %s", s));
    }
    return result;
  }
}
