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

package tech.pegasys.artemis.pow.contract;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.2.0.
 */
public class DepositContract extends Contract {
  private static final String BINARY =
      "0x600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a052341561009e57600080fd5b6101406000601f818352015b600061014051602081106100bd57600080fd5b600060c052602060c020015460208261016001015260208101905061014051602081106100e957600080fd5b600060c052602060c020015460208261016001015260208101905080610160526101609050602060c0825160208401600060025af161012757600080fd5b60c0519050606051600161014051018060405190131561014657600080fd5b809190121561015457600080fd5b6020811061016157600080fd5b600060c052602060c0200155606051600161014051018060405190131561018757600080fd5b809190121561019557600080fd5b602081106101a257600080fd5b600060c052602060c020015460605160016101405101806040519013156101c857600080fd5b80919012156101d657600080fd5b602081106101e357600080fd5b600160c052602060c02001555b81516001018083528114156100aa575b50506110fb56600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a0526380673289600051141561026b57602060046101403734156100b457600080fd5b67ffffffffffffffff6101405111156100cc57600080fd5b60006101605261014051610180526101a060006008818352015b6101605160086000811215610103578060000360020a820461010a565b8060020a82025b905090506101605260ff61018051166101c052610160516101c0516101605101101561013557600080fd5b6101c051610160510161016052610180517ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8600081121561017e578060000360020a8204610185565b8060020a82025b90509050610180525b81516001018083528114156100e6575b505060186008602082066101e001602082840111156101bc57600080fd5b60208061020082610160600060046015f15050818152809050905090508051602001806102a0828460006004600a8704601201f16101f957600080fd5b50506102a05160206001820306601f82010390506103006102a0516008818352015b8261030051111561022b57610247565b6000610300516102c001535b815160010180835281141561021b575b50505060206102805260406102a0510160206001820306601f8201039050610280f3005b639d70e8066000511415610405576020600461014037341561028c57600080fd5b60286004356004016101603760086004356004013511156102ac57600080fd5b60006101c0526101608060200151600082518060209013156102cd57600080fd5b80919012156102db57600080fd5b806020036101000a82049050905090506101e05261020060006008818352015b60ff6101e05116606051606051610200516007038060405190131561031f57600080fd5b809190121561032d57600080fd5b6008028060405190131561034057600080fd5b809190121561034e57600080fd5b6000811215610365578060000360020a820461036c565b8060020a82025b90509050610220526101c051610220516101c05101101561038c57600080fd5b610220516101c051016101c0526101e0517ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff860008112156103d5578060000360020a82046103dc565b8060020a82025b905090506101e0525b81516001018083528114156102fb575b50506101c05160005260206000f3005b63c5f2892f600051141561055d57341561041e57600080fd5b6000610140526002546101605261018060006020818352015b60016001610160511614156104b8576000610180516020811061045957600080fd5b600160c052602060c02001546020826102200101526020810190506101405160208261022001015260208101905080610220526102209050602060c0825160208401600060025af16104aa57600080fd5b60c051905061014052610526565b6000610140516020826101a001015260208101905061018051602081106104de57600080fd5b600060c052602060c02001546020826101a0010152602081019050806101a0526101a09050602060c0825160208401600060025af161051c57600080fd5b60c0519050610140525b610160600261053457600080fd5b60028151048152505b8151600101808352811415610437575b50506101405160005260206000f3005b63621fd130600051141561063357341561057657600080fd5b60606101c060246380673289610140526002546101605261015c6000305af161059e57600080fd5b6101e0805160200180610260828460006004600a8704601201f16105c157600080fd5b50506102605160206001820306601f82010390506102c0610260516008818352015b826102c05111156105f35761060f565b60006102c05161028001535b81516001018083528114156105e3575b5050506020610240526040610260510160206001820306601f8201039050610240f3005b6398b1e06a6000511415610ec857602060046101403760d86004356004016101603760b860043560040135111561066957600080fd5b602061058060646020639d70e8066104c052806104e05260506008602082066103a00161016051828401111561069e57600080fd5b60b8806103c08260206020880688030161016001600060046024f150508181528090509050905080805160200180846104e001828460006004600a8704601201f16106e857600080fd5b50508051820160206001820306601f82010390506020019150506104dc90506000305af161071557600080fd5b6105805161026052633b9aca006105a0526105a05161073357600080fd5b6105a0513404610260511461074757600080fd5b633b9aca0061026051101561075b57600080fd5b64077359400061026051111561077057600080fd5b6002546105c05260006105e05260026106005261062060006020818352015b60006106005161079e57600080fd5b610600516105c05160016105c0510110156107b857600080fd5b60016105c05101061415156107cc57610838565b6105e06060516001825101806040519013156107e757600080fd5b80919012156107f557600080fd5b8152506106008051151561080a576000610824565b600281516002835102041461081e57600080fd5b60028151025b8152505b815160010180835281141561078f575b5050610160602060c0825160208401600060025af161085657600080fd5b60c05190506106405261066060006020818352015b6105e0516106605112156108eb576000610660516020811061088c57600080fd5b600160c052602060c02001546020826106800101526020810190506106405160208261068001015260208101905080610680526106809050602060c0825160208401600060025af16108dd57600080fd5b60c0519050610640526108f0565b610901565b5b815160010180835281141561086b575b5050610640516105e0516020811061091857600080fd5b600160c052602060c0200155600280546001825401101561093857600080fd5b60018154018155506020610780600463c5f2892f6107205261073c6000305af161096157600080fd5b61078051610700526060610820602463806732896107a0526105c0516107c0526107bc6000305af161099257600080fd5b610840805160200180610880828460006004600a8704601201f16109b557600080fd5b505060406109005261090051610940526101608051602001806109005161094001828460006004600a8704601201f16109ed57600080fd5b505061090051610940015160206001820306601f820103905061090051610940016108e0815160c0818352015b836108e051101515610a2b57610a48565b60006108e0516020850101535b8151600101808352811415610a1a575b50505050602061090051610940015160206001820306601f82010390506109005101016109005261090051610960526108808051602001806109005161094001828460006004600a8704601201f1610a9f57600080fd5b505061090051610940015160206001820306601f820103905061090051610940016108e081516020818352015b836108e051101515610add57610afa565b60006108e0516020850101535b8151600101808352811415610acc575b50505050602061090051610940015160206001820306601f8201039050610900510101610900527f42dc88172194fbb332e0cb2fd0d4411b0b44a152a0d05a406b6790641bdefec061090051610940a1640773594000610260511415610ec6576003805460018254011015610b6e57600080fd5b6001815401815550620100006003541415610ec557426109a052426109c05262015180610b9a57600080fd5b620151806109c051066109a0511015610bb257600080fd5b426109c05262015180610bc457600080fd5b620151806109c051066109a051036202a300426109a052426109c05262015180610bed57600080fd5b620151806109c051066109a0511015610c0557600080fd5b426109c05262015180610c1757600080fd5b620151806109c051066109a05103011015610c3157600080fd5b6202a300426109a052426109c05262015180610c4c57600080fd5b620151806109c051066109a0511015610c6457600080fd5b426109c05262015180610c7657600080fd5b620151806109c051066109a0510301610980526060610a60602463806732896109e052600254610a00526109fc6000305af1610cb157600080fd5b610a80805160200180610ac0828460006004600a8704601201f1610cd457600080fd5b50506060610ba060246380673289610b205261098051610b4052610b3c6000305af1610cff57600080fd5b610bc0805160200180610c00828460006004600a8704601201f1610d2257600080fd5b505061070051610cc0526060610c8052610c8051610ce052610ac0805160200180610c8051610cc001828460006004600a8704601201f1610d6257600080fd5b5050610c8051610cc0015160206001820306601f8201039050610c8051610cc001610c6081516020818352015b83610c6051101515610da057610dbd565b6000610c60516020850101535b8151600101808352811415610d8f575b505050506020610c8051610cc0015160206001820306601f8201039050610c80510101610c8052610c8051610d0052610c00805160200180610c8051610cc001828460006004600a8704601201f1610e1457600080fd5b5050610c8051610cc0015160206001820306601f8201039050610c8051610cc001610c6081516020818352015b83610c6051101515610e5257610e6f565b6000610c60516020850101535b8151600101808352811415610e41575b505050506020610c8051610cc0015160206001820306601f8201039050610c80510101610c80527f08b71ef3f1b58f7a23ffb82e27f12f0888c8403f1ceb0ea7ea26b274e2189d4c610c8051610cc0a160016004555b5b005b63845980e86000511415610eee573415610ee157600080fd5b60045460005260206000f3005b60006000fd5b6102076110fb036102076000396102076110fb036000f3";

  public static final String FUNC_TO_LITTLE_ENDIAN_64 = "to_little_endian_64";

  public static final String FUNC_FROM_LITTLE_ENDIAN_64 = "from_little_endian_64";

  public static final String FUNC_GET_DEPOSIT_ROOT = "get_deposit_root";

  public static final String FUNC_GET_DEPOSIT_COUNT = "get_deposit_count";

  public static final String FUNC_DEPOSIT = "deposit";

  public static final String FUNC_CHAINSTARTED = "chainStarted";

  public static final Event DEPOSIT_EVENT =
      new Event(
          "Deposit",
          Arrays.asList(
              new TypeReference<DynamicBytes>() {}, new TypeReference<DynamicBytes>() {}));

  public static final Event ETH2GENESIS_EVENT =
      new Event(
          "Eth2Genesis",
          Arrays.asList(
              new TypeReference<Bytes32>() {},
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {}));

  @Deprecated
  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public List<DepositEventResponse> getDepositEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(DEPOSIT_EVENT, transactionReceipt);
    ArrayList<DepositEventResponse> responses = new ArrayList<>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      DepositEventResponse typedResponse = new DepositEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.data = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.merkle_tree_index =
          (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<DepositEventResponse> depositEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            log -> {
              EventValuesWithLog eventValues = extractEventParametersWithLog(DEPOSIT_EVENT, log);
              DepositEventResponse typedResponse = new DepositEventResponse();
              typedResponse.log = log;
              typedResponse.data = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
              typedResponse.merkle_tree_index =
                  (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
              return typedResponse;
            });
  }

  public Flowable<DepositEventResponse> depositEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(DEPOSIT_EVENT));
    return depositEventFlowable(filter);
  }

  public List<Eth2GenesisEventResponse> getEth2GenesisEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(ETH2GENESIS_EVENT, transactionReceipt);
    ArrayList<Eth2GenesisEventResponse> responses = new ArrayList<>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      Eth2GenesisEventResponse typedResponse = new Eth2GenesisEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.deposit_root = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.deposit_count = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse.time = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<Eth2GenesisEventResponse> eth2GenesisEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            log -> {
              EventValuesWithLog eventValues =
                  extractEventParametersWithLog(ETH2GENESIS_EVENT, log);
              Eth2GenesisEventResponse typedResponse = new Eth2GenesisEventResponse();
              typedResponse.log = log;
              typedResponse.deposit_root =
                  (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
              typedResponse.deposit_count =
                  (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
              typedResponse.time = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
              return typedResponse;
            });
  }

  public Flowable<Eth2GenesisEventResponse> eth2GenesisEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ETH2GENESIS_EVENT));
    return eth2GenesisEventFlowable(filter);
  }

  @SuppressWarnings({"rawtypes"})
  public RemoteCall<byte[]> to_little_endian_64(BigInteger value) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_TO_LITTLE_ENDIAN_64,
            Collections.singletonList(new Uint256(value)),
            Collections.singletonList(new TypeReference<DynamicBytes>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  @SuppressWarnings({"rawtypes"})
  public RemoteCall<BigInteger> from_little_endian_64(byte[] value) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_FROM_LITTLE_ENDIAN_64,
            Arrays.asList(new DynamicBytes(value)),
            Arrays.asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  @SuppressWarnings({"rawtypes"})

  public RemoteCall<byte[]> get_deposit_root() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GET_DEPOSIT_ROOT,
            Collections.emptyList(),
            Collections.singletonList(new TypeReference<Bytes32>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  @SuppressWarnings({"rawtypes"})
  public RemoteCall<byte[]> get_deposit_count() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GET_DEPOSIT_COUNT,
            Collections.emptyList(),
            Collections.singletonList(new TypeReference<DynamicBytes>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  @SuppressWarnings({"rawtypes"})
  public RemoteCall<TransactionReceipt> deposit(byte[] deposit_input, BigInteger weiValue) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_DEPOSIT,
            Collections.singletonList(new DynamicBytes(deposit_input)),
            Collections.emptyList());
    return executeRemoteCallTransaction(function, weiValue);
  }

  @SuppressWarnings({"rawtypes"})
  public RemoteCall<Boolean> chainStarted() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_CHAINSTARTED,
            Collections.emptyList(),
            Collections.singletonList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
  }

  @Deprecated
  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DepositContract(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DepositContract(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new DepositContract(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new DepositContract(contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<DepositContract> deploy(
      Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DepositContract.class, web3j, credentials, contractGasProvider, BINARY, "");
  }

  public static RemoteCall<DepositContract> deploy(
      Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DepositContract.class, web3j, transactionManager, contractGasProvider, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<DepositContract> deploy(
      Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
    return deployRemoteCall(
        DepositContract.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<DepositContract> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return deployRemoteCall(
        DepositContract.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
  }

  public static class DepositEventResponse {
    public Log log;

    public byte[] data;

    public byte[] merkle_tree_index;
  }

  public static class Eth2GenesisEventResponse {
    public Log log;

    public byte[] deposit_root;

    public byte[] deposit_count;

    public byte[] time;
  }
}
