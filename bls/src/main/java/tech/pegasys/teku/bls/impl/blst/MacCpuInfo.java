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

package tech.pegasys.teku.bls.impl.blst;

import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;

public class MacCpuInfo {

  public static boolean supportsOptimisedBlst() throws IOException {
    return macHasCpuFeature("arm64") || (macHasCpuFeature("bmi2") && macHasCpuFeature("adx"));
  }

  private static boolean macHasCpuFeature(final String cpuFeature) throws IOException {
    final Process process =
        new ProcessBuilder("/usr/sbin/sysctl", "-n", "hw.optional." + cpuFeature)
            .redirectErrorStream(true)
            .start();
    final String output = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());
    return process.exitValue() == 0 && output != null && output.trim().equals("1");
  }
}
