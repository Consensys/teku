/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.cli.subcommand;

import com.google.common.annotations.VisibleForTesting;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.util.cli.PicoCliVersionProvider;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

@Command(
    name = "peer",
    description = "Commands for LibP2P PeerID",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class PeerCommand {

  @Command(
      name = "generate",
      description = "Generate a list of peer ids",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public void generate(
      @Mixin PeerGenerationParams params,
      @Option(
              names = {"-n", "--number"},
              arity = "1",
              required = true,
              description = "number of peerIDs to generate")
          int number)
      throws IOException {
    try {
      File f = new File(params.outputFile);
      if (f.exists()) {
        throw new InvalidConfigurationException(
            String.format(
                "Not overwriting existing file %s \nDelete file or use --output-file to point to a file that does not currently exist.",
                params.outputFile));
      }
      FileWriter fileWriter = new FileWriter(params.outputFile, Charset.defaultCharset());
      PrintWriter printWriter = new PrintWriter(fileWriter);
      printWriter.println("Private Key(Hex)\tPublic Key(Hex)\tPeerId(Base58)");
      for (int i = 0; i < number; i++) {
        PrivKey privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
        PubKey pubKey = privKey.publicKey();
        PeerId peerId = PeerId.fromPubKey(pubKey);
        printWriter.println(
            Bytes.wrap(privKey.bytes()).toHexString()
                + "\t"
                + Bytes.wrap(pubKey.bytes()).toHexString()
                + "\t"
                + peerId.toBase58());
      }
      printWriter.close();
    } catch (FileNotFoundException ex) {
      throw new InvalidConfigurationException(
          "use --output-file to point to a file in an existing directory " + ex.getMessage());
    }
  }

  public static class PeerGenerationParams {

    @Option(
        names = {"-o", "--output-file"},
        paramLabel = "<FILENAME>",
        description = "Path/filename of the output file")
    private String outputFile = "./config/peer-ids.dat";

    @VisibleForTesting
    protected PeerGenerationParams(final String outputFile) {
      super();
      this.outputFile = outputFile;
    }

    PeerGenerationParams() {
      super();
    }
  }
}
