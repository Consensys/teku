/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "Artemis", mixinStandardHelpOptions = true, description = "@|bold Groovy|@ @|underline picocli|@ example")
public class CommandLineArguments implements Runnable{
    @Option(names = {"-p", "--simPoWChain"}, description = "If this option is enabled them PoW chain events are simulated.")
    private Boolean simulatedPoWChainEvents=false;

    @Override
    public void run(){
        System.out.println("SimulatedPoWEvents: " + this.simulatedPoWChainEvents);
    }

}
