// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package com.google.crypto.tink.integration.awskms;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.aead.AeadConfig;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.kms.model.KmsException;

@RunWith(JUnit4.class)
public final class FakeAwsKmsTest {

  private static final String KEY_ID =
      "arn:aws:kms:us-west-2:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab";
  private static final String KEY_ID_2 = "arn:aws:kms:us-west-2:123:key/different";

  @BeforeClass
  public static void setUpClass() throws Exception {
    AeadConfig.register();
  }

  @Test
  public void testEncryptDecryptWithValidKeyId_success() throws Exception {
    KmsClient kms = new FakeAwsKms(asList(KEY_ID));

    byte[] plaintext = "plaintext".getBytes(UTF_8);

    Map<String, String> context = new HashMap<>();
    context.put("name", "value");

    EncryptRequest encRequest =
        EncryptRequest.builder()
            .keyId(KEY_ID)
            .plaintext(SdkBytes.fromByteArray(plaintext))
            .encryptionContext(context)
            .build();
    EncryptResponse encResult = kms.encrypt(encRequest);
    assertThat(encResult.keyId()).isEqualTo(KEY_ID);

    DecryptRequest decRequest =
        DecryptRequest.builder()
            .ciphertextBlob(encResult.ciphertextBlob())
            .encryptionContext(context)
            .build();

    DecryptResponse decResult = kms.decrypt(decRequest);
    assertThat(decResult.keyId()).isEqualTo(KEY_ID);
    assertThat(decResult.plaintext().asByteArray()).isEqualTo(plaintext);
  }

  @Test
  public void testEncryptWithInvalidKeyId_fails() throws Exception {
    KmsClient kms = new FakeAwsKms(asList(KEY_ID));

    byte[] plaintext = "plaintext".getBytes(UTF_8);

    Map<String, String> context = new HashMap<>();
    context.put("name", "value");

    EncryptRequest encRequestWithDifferentKeyArn =
        EncryptRequest.builder()
            .keyId(KEY_ID_2)
            .plaintext(SdkBytes.fromByteArray(plaintext))
            .encryptionContext(context)
            .build();
    assertThrows(KmsException.class, () -> kms.encrypt(encRequestWithDifferentKeyArn));
  }

  @Test
  public void testDecryptWithInvalidKeyId_fails() throws Exception {
    KmsClient kms = new FakeAwsKms(asList(KEY_ID));

    byte[] invalidCiphertext = "invalid".getBytes(UTF_8);

    Map<String, String> context = new HashMap<>();
    context.put("name", "value");

    DecryptRequest decRequestWithInvalidCiphertext =
        DecryptRequest.builder()
            .ciphertextBlob(SdkBytes.fromByteArray(invalidCiphertext))
            .encryptionContext(context)
            .build();
    assertThrows(KmsException.class, () -> kms.decrypt(decRequestWithInvalidCiphertext));
  }


  @Test
  public void testEncryptDecryptWithTwoValidKeyId_success() throws Exception {
    KmsClient kms = new FakeAwsKms(asList(KEY_ID, KEY_ID_2));

    byte[] plaintext = "plaintext".getBytes(UTF_8);
    byte[] plaintext2 = "plaintext2".getBytes(UTF_8);

    Map<String, String> context = new HashMap<>();
    context.put("name", "value");

    EncryptRequest encRequest =
        EncryptRequest.builder()
            .keyId(KEY_ID)
            .plaintext(SdkBytes.fromByteArray(plaintext))
            .encryptionContext(context)
            .build();
    EncryptResponse encResult = kms.encrypt(encRequest);
    assertThat(encResult.keyId()).isEqualTo(KEY_ID);

    EncryptRequest encRequest2 =
        EncryptRequest.builder()
            .keyId(KEY_ID_2)
            .plaintext(SdkBytes.fromByteArray(plaintext2))
            .encryptionContext(context)
            .build();
    EncryptResponse encResult2 = kms.encrypt(encRequest2);
    assertThat(encResult2.keyId()).isEqualTo(KEY_ID_2);

    DecryptRequest decRequest =
        DecryptRequest.builder()
            .ciphertextBlob(encResult.ciphertextBlob())
            .encryptionContext(context)
            .build();

    DecryptResponse decResult = kms.decrypt(decRequest);
    assertThat(decResult.keyId()).isEqualTo(KEY_ID);
    assertThat(decResult.plaintext().asByteArray()).isEqualTo(plaintext);

    DecryptRequest decRequest2 =
        DecryptRequest.builder()
            .ciphertextBlob(encResult2.ciphertextBlob())
            .encryptionContext(context)
            .build();

    DecryptResponse decResult2 = kms.decrypt(decRequest2);
    assertThat(decResult2.keyId()).isEqualTo(KEY_ID_2);
    assertThat(decResult2.plaintext().asByteArray()).isEqualTo(plaintext2);
  }

}
