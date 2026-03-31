// Copyright 2026 Google LLC
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
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.subtle.Hex;
import java.security.GeneralSecurityException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests Tink's AwsKmsClient with the real AWS KMS. */
@RunWith(JUnit4.class)
public class AwsKmsIntegrationTest {

  // This integration test can be successfully executed when this file contains credentials for an
  // AWS user which has access to the keys specified in {@link #AWS_KMS_TEST_KEY_URI} and
  // {@link #AWS_KMS_TEST_KEY_URI_2}.
  private static final String AWS_CREDENTIALS_FILE = "testdata/aws/credentials.cred";

  // A valid AWS KMS AEAD key URI.
  private static final String AWS_KMS_TEST_KEY_URI =
      "aws-kms://arn:aws:kms:us-east-2:235739564943:key/3ee50705-5a82-4f5b-9753-05c4f473922f";

  // Another valid AWS KMS AEAD key URI in the same region as {@link #AWS_KMS_TEST_KEY_URI}.
  private static final String AWS_KMS_TEST_KEY_URI_2 =
      "aws-kms://arn:aws:kms:us-east-2:235739564943:key/b3ca2efd-a8fb-47f2-b541-7e20f8c5cd11";

  @Test
  public void getAead_encryptDecrypt_works() throws Exception {
    byte[] plaintext = "plaintext".getBytes(UTF_8);
    byte[] associatedData = "associatedData".getBytes(UTF_8);

    AwsKmsClient client = new AwsKmsClient();
    client.withCredentials(AWS_CREDENTIALS_FILE);
    Aead aead = client.getAead(AWS_KMS_TEST_KEY_URI);

    byte[] ciphertext = aead.encrypt(plaintext, associatedData);
    byte[] decrypted = aead.decrypt(ciphertext, associatedData);
    assertThat(decrypted).isEqualTo(plaintext);

    byte[] emptyAssociatedData = "".getBytes(UTF_8);
    assertThat(aead.decrypt(aead.encrypt(plaintext, emptyAssociatedData), emptyAssociatedData))
        .isEqualTo(plaintext);

    assertThrows(
        GeneralSecurityException.class,
        () -> aead.decrypt(ciphertext, "wrongAssociatedData".getBytes(UTF_8)));
    assertThrows(
        GeneralSecurityException.class,
        () -> aead.decrypt("invalidCiphertext".getBytes(UTF_8), associatedData));
    // empty ciphertext throws GeneralSecurityException.
    byte[] emptyCiphertext = "".getBytes(UTF_8);
    assertThrows(GeneralSecurityException.class, () -> aead.decrypt(emptyCiphertext, associatedData));
  }


  @Test
  public void encryptEmptyPlaintext_mayThrowGeneralSecurityException() throws Exception {
    byte[] emptyPlaintext = "".getBytes(UTF_8);
    byte[] associatedData = "associatedData".getBytes(UTF_8);

    AwsKmsClient client = new AwsKmsClient();
    client.withCredentials(AWS_CREDENTIALS_FILE);
    Aead aead = client.getAead(AWS_KMS_TEST_KEY_URI);

    try {
      byte[] unusedCiphertext = aead.encrypt(emptyPlaintext, associatedData);
    } catch (GeneralSecurityException e) {
      // This currently does throw this exception, but it may not in the future.
      // This tests verifies that if it throw, it is a GeneralSecurityException.
    }
  }

  @Test
  public void decrypt_ciphertextEncryptedWithDifferentKeyUri_fails() throws Exception {
    byte[] plaintext = "plaintext".getBytes(UTF_8);
    byte[] associatedData = "associatedData".getBytes(UTF_8);

    AwsKmsClient client = new AwsKmsClient();
    client.withCredentials(AWS_CREDENTIALS_FILE);
    Aead aead = client.getAead(AWS_KMS_TEST_KEY_URI);
    byte[] ciphertext = aead.encrypt(plaintext, associatedData);
    Aead aead2 = client.getAead(AWS_KMS_TEST_KEY_URI_2);
    byte[] ciphertext2 = aead2.encrypt(plaintext, associatedData);

    // Check that ciphertexts are valid.
    assertThat(aead.decrypt(ciphertext, associatedData)).isEqualTo(plaintext);
    assertThat(aead2.decrypt(ciphertext2, associatedData)).isEqualTo(plaintext);

    // ciphertexts cannot be decrypted with a different key URI.
    assertThrows(GeneralSecurityException.class, () -> aead2.decrypt(ciphertext, associatedData));
    assertThrows(GeneralSecurityException.class, () -> aead.decrypt(ciphertext2, associatedData));
  }

  @Test
  public void decrypt_testVector_works() throws Exception {
    AwsKmsClient client = new AwsKmsClient();
    client.withCredentials(AWS_CREDENTIALS_FILE);
    Aead aead = client.getAead(AWS_KMS_TEST_KEY_URI);

    // Same ciphertext as in aws_kms_integration_test.go of tink-go-awskms/v3.
    byte[] ciphertextTestVector =
        Hex.decode(
            "01020200787a5511910e44ea9520df697fb6150f6261b59cee44cc846915252c083f03aad00168fe1a9ca1a6af5d571c1aa6e0afcba000000067306506092a864886f70d010706a0583056020100305106092a864886f70d010701301e060960864801650304012e3011040cb085893bc4757850d73a68ca02011080241fcff6b61f510d20c236ee7292b626e35a22b615e57326911daafaa399525431748d3e24");
    byte[] decrypted = aead.decrypt(ciphertextTestVector, "associatedData".getBytes(UTF_8));
    assertThat(decrypted).isEqualTo("plaintext".getBytes(UTF_8));
  }
}
