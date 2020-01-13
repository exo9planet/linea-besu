/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.SendTransactionResponse;

import java.math.BigInteger;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.impl.JWTUser;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EeaSendRawTransactionTest {

  private static final String VALUE_NON_ZERO_TRANSACTION_RLP =
      "0xf88b808203e8832dc6c0808203e880820fe8a08b89005561f31ce861"
          + "84949bf32087a9817b337ab1d6027d58ef4e48aea88bafa041b93a"
          + "e41a99fe662ad7fc1406ac90bf6bd498b5fe56fd6bfea15de15714"
          + "438eac41316156744d784c4355486d425648586f5a7a7a42675062"
          + "572f776a3561784470573958386c393153476f3dc08a7265737472"
          + "6963746564";

  private static final String VALID_PRIVATE_TRANSACTION_RLP =
      "0xf8f3800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "808025a048b55bfa915ac795c431978d8a6a992b628d557da5ff"
          + "759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56"
          + "c0b28ad43601b4ab949f53faa07bd2c804ac41316156744d784c"
          + "4355486d425648586f5a7a7a42675062572f776a356178447057"
          + "3958386c393153476f3df85aac41316156744d784c4355486d42"
          + "5648586f5a7a7a42675062572f776a3561784470573958386c39"
          + "3153476f3dac4b6f32625671442b6e4e6c4e594c354545377933"
          + "49644f6e766966746a69697a706a52742b4854754642733d8a72"
          + "657374726963746564";

  private static final String VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP =
      "0xf8ac800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "80801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff"
          + "759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56"
          + "c0b28ad43601b4ab949f53faa07bd2c804a0035695b4cc4b0941"
          + "e60551d7a19cf30603db5bfc23e5ac43a56f57f25f75486aa00f"
          + "200e885ff29e973e2576b6600181d1b0a2b5294e30d9be4a1981"
          + "ffb33a0b8c8a72657374726963746564";

  private static final String PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP_NO_PRIVATE_FROM =
      "0xf88b800182520894095e7baea6a6c7c4c2dfeb977efac326af55"
          + "2d8780801ba048b55bfa915ac795c431978d8a6a992b628d55"
          + "7da5ff759b307d495a36649353a01fffd310ac743f371de3b9"
          + "f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804a00f200e"
          + "885ff29e973e2576b6600181d1b0a2b5294e30d9be4a1981ff"
          + "b33a0b8c8a72657374726963746564";

  private static final Transaction PUBLIC_TRANSACTION =
      new Transaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.ZERO,
          SECP256K1.Signature.create(
              new BigInteger(
                  "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
              new BigInteger(
                  "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
              Byte.parseByte("0")),
          Bytes.fromHexString("0x"),
          Address.wrap(Bytes.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          Optional.empty());
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  private final String MOCK_ORION_KEY = "";
  private final String MOCK_PRIVACY_GROUP = "";
  private final User user =
      new JWTUser(new JsonObject().put("privacyPublicKey", ENCLAVE_PUBLIC_KEY), "");
  private final EnclavePublicKeyProvider enclavePublicKeyProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  @Mock private TransactionPool transactionPool;

  @Mock private EeaSendRawTransaction method;

  @Mock private PrivacyController privacyController;

  @Before
  public void before() {
    method =
        new EeaSendRawTransaction(transactionPool, privacyController, enclavePublicKeyProvider);
  }

  @Test
  public void requestIsMissingParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestHasNullObjectParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eea_sendRawTransaction", null));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {null}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void invalidTransactionRlpDecoding() {
    final String rawTransaction = "0x00";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {rawTransaction}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.DECODE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void valueNonZeroTransaction() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", "eea_sendRawTransaction", new String[] {VALUE_NON_ZERO_TRANSACTION_RLP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.VALUE_NOT_ZERO);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void validTransactionIsSentToTransactionPool() {
    when(privacyController.sendTransaction(any(PrivateTransaction.class), any()))
        .thenReturn(new SendTransactionResponse(MOCK_ORION_KEY, MOCK_PRIVACY_GROUP));
    when(privacyController.validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class), any()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", "eea_sendRawTransaction", new String[] {VALID_PRIVATE_TRANSACTION_RLP}),
            user);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0x221e930a2c18d91fca4d509eaa3512f3e01fef266f660e32473de67474b36c15");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privacyController)
        .sendTransaction(any(PrivateTransaction.class), eq(ENCLAVE_PUBLIC_KEY));
    verify(privacyController)
        .validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class), eq(ENCLAVE_PUBLIC_KEY));
    verify(privacyController)
        .createPrivacyMarkerTransaction(any(String.class), any(PrivateTransaction.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void validTransactionPrivacyGroupIsSentToTransactionPool() {
    when(privacyController.sendTransaction(any(PrivateTransaction.class), any()))
        .thenReturn(new SendTransactionResponse(MOCK_ORION_KEY, MOCK_PRIVACY_GROUP));
    when(privacyController.validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class), any()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "eea_sendRawTransaction",
                new String[] {VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0x221e930a2c18d91fca4d509eaa3512f3e01fef266f660e32473de67474b36c15");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privacyController).sendTransaction(any(PrivateTransaction.class), any());
    verify(privacyController)
        .validatePrivateTransaction(any(PrivateTransaction.class), any(String.class), any());
    verify(privacyController)
        .createPrivacyMarkerTransaction(any(String.class), any(PrivateTransaction.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void invalidTransactionWithoutPrivateFromFieldFailsWithDecodeError() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "eea_sendRawTransaction",
                new String[] {PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP_NO_PRIVATE_FROM}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.DECODE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verifyNoInteractions(privacyController);
  }

  @Test
  public void invalidTransactionIsNotSentToTransactionPool() {
    when(privacyController.sendTransaction(any(PrivateTransaction.class), any()))
        .thenThrow(new EnclaveClientException(400, "enclave failed to execute"));

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", "eea_sendRawTransaction", new String[] {VALID_PRIVATE_TRANSACTION_RLP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.ENCLAVE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verifyNoInteractions(transactionPool);
  }

  @Test
  public void invalidTransactionFailingWithMultiTenancyValidationErrorReturnsUnauthorizedError() {
    when(privacyController.sendTransaction(any(PrivateTransaction.class), any()))
        .thenThrow(new MultiTenancyValidationException("validation failed"));

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", "eea_sendRawTransaction", new String[] {VALID_PRIVATE_TRANSACTION_RLP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.ENCLAVE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verifyNoInteractions(transactionPool);
  }

  @Test
  public void transactionWithNonceBelowAccountNonceIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.NONCE_TOO_LOW, JsonRpcError.NONCE_TOO_LOW);
  }

  @Test
  public void transactionWithNonceAboveAccountNonceIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INCORRECT_NONCE, JsonRpcError.INCORRECT_NONCE);
  }

  @Test
  public void transactionWithInvalidSignatureIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INVALID_SIGNATURE, JsonRpcError.INVALID_TRANSACTION_SIGNATURE);
  }

  @Test
  public void transactionWithIntrinsicGasExceedingGasLimitIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
        JsonRpcError.INTRINSIC_GAS_EXCEEDS_LIMIT);
  }

  @Test
  public void transactionWithUpfrontGasExceedingAccountBalanceIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE,
        JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);
  }

  @Test
  public void transactionWithGasLimitExceedingBlockGasLimitIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT, JsonRpcError.EXCEEDS_BLOCK_GAS_LIMIT);
  }

  @Test
  public void transactionWithNotWhitelistedSenderAccountIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED, JsonRpcError.TX_SENDER_NOT_AUTHORIZED);
  }

  private void verifyErrorForInvalidTransaction(
      final TransactionInvalidReason transactionInvalidReason, final JsonRpcError expectedError) {

    when(privacyController.sendTransaction(any(PrivateTransaction.class), any()))
        .thenReturn(new SendTransactionResponse(MOCK_ORION_KEY, MOCK_PRIVACY_GROUP));
    when(privacyController.validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class), any()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.invalid(transactionInvalidReason));
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", "eea_sendRawTransaction", new String[] {VALID_PRIVATE_TRANSACTION_RLP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), expectedError);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privacyController).sendTransaction(any(PrivateTransaction.class), any());
    verify(privacyController)
        .validatePrivateTransaction(any(PrivateTransaction.class), any(String.class), any());
    verify(privacyController)
        .createPrivacyMarkerTransaction(any(String.class), any(PrivateTransaction.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void getMethodReturnsExpectedName() {
    assertThat(method.getName()).matches("eea_sendRawTransaction");
  }
}
