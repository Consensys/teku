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

package tech.pegasys.artemis.pow.contract;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.StaticArray8;
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
import rx.Observable;
import rx.functions.Func1;


/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 3.6.0.
 */
public class ValidatorRegistrationContract extends Contract {
    private static final String BINARY = "6109c156600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a0526398b1e06a600051141561088e576020600461014037610820600435600401610160376108006004356004013511156100cb57600080fd5b670de0b6b3a76400003410156100e057600080fd5b6801bc16d674ec8000003411156100f657600080fd5b600154602060020a6002106020156001602014171761011457600080fd5b602060020a60015401101561012857600080fd5b602060020a6002106020156001602014171761014357600080fd5b602060020a600154016109a0526018600860208206610ac0016000633b9aca0061016c57600080fd5b633b9aca003404602082610a6001015260208101905080610a6052610a60905051828401111561019b57600080fd5b602080610ae0826020602088068803016000633b9aca006101bb57600080fd5b633b9aca003404602082610a6001015260208101905080610a6052610a60905001600060046015f15050818152809050905090508051602001806109c0828460006004600a8704601201f161020f57600080fd5b50506018600860208206610c4001600042602082610be001015260208101905080610be052610be0905051828401111561024857600080fd5b602080610c6082602060208806880301600042602082610be001015260208101905080610be052610be0905001600060046015f1505081815280905090509050805160200180610b40828460006004600a8704601201f16102a857600080fd5b505060006109c060088060208461152001018260208501600060046012f1505080518201915050610b4060088060208461152001018260208501600060046012f150508051820191505061016061080080602084611520010182602085016000600460def150508051820191505080611520526115209050805160200180610cc0828460006004600a8704601201f161034057600080fd5b50506000600160e05260c052604060c02054611de052600154611e20526060611da052611da051611e0052610cc0805160200180611da051611de001828460006004600a8704601201f161039357600080fd5b5050611da051611de001611d808151610820818352015b610820611d80511015156103bd576103da565b6000611d80516020850101535b81516001018083528114156103aa575b5050506020611da051611de0015160206001820306601f8201039050611da0510101611da0527f59c0d58bdbb63aefe0556ee65fae318ed89c2c1f00805ae81da404456b7f8d0e611da051611de0a1610cc080516020820120905060006109a05160e05260c052604060c02055611e4060006020818352015b6109a0600261046157600080fd5b6002815104815250600060006109a051151561047e57600061049e565b60026109a05160026109a05102041461049657600080fd5b60026109a051025b60e05260c052604060c02054602082611e6001015260208101905060006109a05115156104cc5760006104ec565b60026109a05160026109a0510204146104e457600080fd5b60026109a051025b60016109a05115156104ff57600061051f565b60026109a05160026109a05102041461051757600080fd5b60026109a051025b01101561052b57600080fd5b60016109a051151561053e57600061055e565b60026109a05160026109a05102041461055657600080fd5b60026109a051025b0160e05260c052604060c02054602082611e6001015260208101905080611e6052611e60905080516020820120905060006109a05160e05260c052604060c020555b8151600101808352811415610453575b505060018054600182540110156105c657600080fd5b60018154018155506801bc16d674ec80000034141561088c5760028054600182540110156105f357600080fd5b6001815401815550614000600254141561088b5742611f005242611f20526201518061061e57600080fd5b62015180611f205106611f0051101561063657600080fd5b42611f20526201518061064857600080fd5b62015180611f205106611f0051036201518042611f005242611f20526201518061067157600080fd5b62015180611f205106611f0051101561068957600080fd5b42611f20526201518061069b57600080fd5b62015180611f205106611f0051030110156106b557600080fd5b6201518042611f005242611f2052620151806106d057600080fd5b62015180611f205106611f005110156106e857600080fd5b42611f2052620151806106fa57600080fd5b62015180611f205106611f00510301611ee0526018600860208206612040016000611ee051602082611fe001015260208101905080611fe052611fe0905051828401111561074757600080fd5b602080612060826020602088068803016000611ee051602082611fe001015260208101905080611fe052611fe0905001600060046015f1505081815280905090509050805160200180611f40828460006004600a8704601201f16107aa57600080fd5b50506000600160e05260c052604060c020546121205260406120e0526120e05161214052611f408051602001806120e05161212001828460006004600a8704601201f16107f657600080fd5b50506120e051612120016120c081516020818352015b60206120c05110151561081e5761083b565b60006120c0516020850101535b815160010180835281141561080c575b50505060206120e051612120015160206001820306601f82010390506120e05101016120e0527fd1faa3f9bca1d698df559716fe6d1c9999155b38d3158fffbc98d76d568091fc6120e051612120a15b5b005b63e21f37ce60005114156109845734156108a757600080fd5b6109a051610a205261029a610a605260606109e0526109e051610a40526101408051602001806109e051610a2001828460006004600a8704601201f16108ec57600080fd5b50506109e051610a20016109c08151610820818352015b6108206109c05110151561091657610933565b60006109c0516020850101535b8151600101808352811415610903575b50505060206109e051610a20015160206001820306601f82010390506109e05101016109e0527f59c0d58bdbb63aefe0556ee65fae318ed89c2c1f00805ae81da404456b7f8d0e6109e051610a20a1005b63ed55bafd60005114156109b757341561099d57600080fd5b6000600160e05260c052604060c0205460005260206000f3005b60006000fd5b6100046109c1036100046000396100046109c1036000f3";

    public static final String FUNC_DEPOSIT = "deposit";

    public static final String FUNC_GET_RECEIPT_ROOT = "get_receipt_root";

    public static final Event ETH1DEPOSIT_EVENT = new Event("Eth1Deposit",
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<DynamicBytes>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event CHAINSTART_EVENT = new Event("ChainStart",
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<StaticArray8<DynamicBytes>>() {}));
    ;


    protected ValidatorRegistrationContract(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    protected ValidatorRegistrationContract(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    @SuppressWarnings("rawtypes")
    public RemoteCall<TransactionReceipt> deposit(byte[] deposit_parameters, BigInteger weiValue) {
        final Function function = new Function(
                FUNC_DEPOSIT,
                Arrays.<Type>asList(new DynamicBytes(deposit_parameters)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function, weiValue);
    }

    @SuppressWarnings("rawtypes")
    public RemoteCall<byte[]> get_receipt_root() {
        final Function function = new Function(FUNC_GET_RECEIPT_ROOT,
                Arrays.<Type>asList(),
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    @SuppressWarnings("unchecked")
    public List<Eth1DepositEventResponse> getEth1DepositEvents(TransactionReceipt transactionReceipt) {
        List<EventValuesWithLog> valueList = extractEventParametersWithLog(ETH1DEPOSIT_EVENT, transactionReceipt);
        ArrayList<Eth1DepositEventResponse> responses = new ArrayList<Eth1DepositEventResponse>(valueList.size());
        for (EventValuesWithLog eventValues : valueList) {
            Eth1DepositEventResponse typedResponse = new Eth1DepositEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.previous_receipt_root = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.data = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.deposit_count = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    @SuppressWarnings("unchecked")
    public Observable<Eth1DepositEventResponse> eth1DepositEventObservable(EthFilter filter) {
        return web3j.ethLogObservable(filter).map(new Func1<Log, Eth1DepositEventResponse>() {
            @Override
            public Eth1DepositEventResponse call(Log log) {
                EventValuesWithLog eventValues = extractEventParametersWithLog(ETH1DEPOSIT_EVENT, log);
                Eth1DepositEventResponse typedResponse = new Eth1DepositEventResponse();
                typedResponse.log = log;
                typedResponse.previous_receipt_root = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.data = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.deposit_count = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
            }
        });
    }

    public Observable<Eth1DepositEventResponse> eth1DepositEventObservable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ETH1DEPOSIT_EVENT));
        return eth1DepositEventObservable(filter);
    }

    @SuppressWarnings("unchecked")
    public List<ChainStartEventResponse> getChainStartEvents(TransactionReceipt transactionReceipt) {
        List<EventValuesWithLog> valueList = extractEventParametersWithLog(CHAINSTART_EVENT, transactionReceipt);
        ArrayList<ChainStartEventResponse> responses = new ArrayList<ChainStartEventResponse>(valueList.size());
        for (EventValuesWithLog eventValues : valueList) {
            ChainStartEventResponse typedResponse = new ChainStartEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.receipt_root = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.time = (List<byte[]>) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    @SuppressWarnings("unchecked")
    public Observable<ChainStartEventResponse> chainStartEventObservable(EthFilter filter) {
        return web3j.ethLogObservable(filter).map(new Func1<Log, ChainStartEventResponse>() {
            @Override
            public ChainStartEventResponse call(Log log) {
                EventValuesWithLog eventValues = extractEventParametersWithLog(CHAINSTART_EVENT, log);
                ChainStartEventResponse typedResponse = new ChainStartEventResponse();
                typedResponse.log = log;
                typedResponse.receipt_root = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.time = (List<byte[]>) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Observable<ChainStartEventResponse> chainStartEventObservable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(CHAINSTART_EVENT));
        return chainStartEventObservable(filter);
    }

    public static RemoteCall<ValidatorRegistrationContract> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(ValidatorRegistrationContract.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    public static RemoteCall<ValidatorRegistrationContract> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(ValidatorRegistrationContract.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    public static ValidatorRegistrationContract load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new ValidatorRegistrationContract(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static ValidatorRegistrationContract load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new ValidatorRegistrationContract(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static class Eth1DepositEventResponse {
        public Log log;

        public byte[] previous_receipt_root;

        public byte[] data;

        public BigInteger deposit_count;
    }

    public static class ChainStartEventResponse {
        public Log log;

        public byte[] receipt_root;

        public List<byte[]> time;
    }
}
