// Copyright 2017 Google Inc.
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
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.Random;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletionException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

/** Tests for AwsKmsAead. */
@RunWith(JUnit4.class)
public class AwsKmsAeadTest {
  private static final String KEY_ARN =
      "arn:aws:kms:us-west-2:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab";
  private static final String KEY_ARN_DIFFERENT = "arn:aws:kms:us-west-2:123:key/different";

  @BeforeClass
  public static void setUpClass() throws Exception {
    AeadConfig.register();
  }

  @Test
  public void testEncryptDecryptWithKnownKeyArn_success() throws Exception {
    KmsClient kms = new FakeAwsKms(asList(KEY_ARN, KEY_ARN_DIFFERENT));

    Aead aead = new AwsKmsAead(kms, KEY_ARN);
    byte[] aad = Random.randBytes(20);
    byte[] message = Random.randBytes(42);
    byte[] ciphertext = aead.encrypt(message, aad);
    byte[] decrypted = aead.decrypt(ciphertext, aad);
    assertThat(decrypted).isEqualTo(message);
  }

  @Test
  public void testEncryptWithUnknownKeyArn_fails() throws Exception {
    KmsClient kmsThatDoentKnowKeyArn = new FakeAwsKms(asList(KEY_ARN_DIFFERENT));

    Aead aead = new AwsKmsAead(kmsThatDoentKnowKeyArn, KEY_ARN);
    byte[] aad = Random.randBytes(20);
    byte[] message = Random.randBytes(20);
    assertThrows(GeneralSecurityException.class, () -> aead.encrypt(message, aad));
  }

  @Test
  public void testDecryptWithInvalidKeyArn_fails() throws Exception {
    KmsClient kms = new FakeAwsKms(asList(KEY_ARN));
    Aead aead = new AwsKmsAead(kms, KEY_ARN);
    byte[] aad = Random.randBytes(20);
    byte[] invalidCiphertext = Random.randBytes(2);
    assertThrows(GeneralSecurityException.class, () -> aead.decrypt(invalidCiphertext, aad));
  }

  @Test
  public void testDecryptWithDifferentKeyArn_fails() throws Exception {
    KmsClient kms = new FakeAwsKms(asList(KEY_ARN, KEY_ARN_DIFFERENT));

    Aead aead = new AwsKmsAead(kms, KEY_ARN);
    byte[] aad = Random.randBytes(20);
    byte[] message = Random.randBytes(20);

    // Create a valid ciphertext with a different ARN
    Aead aeadWithDifferentArn = new AwsKmsAead(kms, KEY_ARN_DIFFERENT);
    byte[] ciphertextFromDifferentArn = aeadWithDifferentArn.encrypt(message, aad);

    assertThrows(
        GeneralSecurityException.class, () -> aead.decrypt(ciphertextFromDifferentArn, aad));
  }

  @Test
  public void testDecryptWithAliasKeyArn_success() throws Exception {
    KmsClient kms = new FakeAwsKms(asList(KEY_ARN));

    byte[] aad = Random.randBytes(20);
    byte[] message = Random.randBytes(20);

    // Create ciphertext for KEY_ARN
    Aead aead = new AwsKmsAead(kms, KEY_ARN);
    byte[] ciphertext = aead.encrypt(message, aad);

    // Use an alias ARN
    String aliasArn = "arn:aws:kms:us-west-2:111122223333:alias/ExampleAlias";
    Aead aeadWithAliasArn = new AwsKmsAead(kms, aliasArn);
    assertThat(aeadWithAliasArn.decrypt(ciphertext, aad)).isEqualTo(message);
  }

  @Test
  public void testDecryptWithInvalidKeyArn_success() throws Exception {
    KmsClient kms = new FakeAwsKms(asList(KEY_ARN));

    byte[] aad = Random.randBytes(20);
    byte[] message = Random.randBytes(20);

    // Create ciphertext for KEY_ARN
    Aead aead = new AwsKmsAead(kms, KEY_ARN);
    byte[] ciphertext = aead.encrypt(message, aad);

    // Use an invalid Key ARN
    // TODO(b/242149560): Make this test case fail
    String invalidArn = "@#$@#$@#";
    Aead aeadWithInvalidArn = new AwsKmsAead(kms, invalidArn);
    assertThat(aeadWithInvalidArn.decrypt(ciphertext, aad)).isEqualTo(message);
  }

  @Test
  public void testEncryptWithCompletionExceptionCause_translatedToGeneralSecurityException()
      throws Exception {
    GeneralSecurityException credentialFailure =
        new GeneralSecurityException("credential refresh failed");
    KmsClient kms = new ThrowingKmsClient(new CompletionException(credentialFailure));
    Aead aead = new AwsKmsAead(kms, KEY_ARN);

    GeneralSecurityException thrown =
        assertThrows(
            GeneralSecurityException.class,
            () -> aead.encrypt(Random.randBytes(20), Random.randBytes(20)));

    assertThat(thrown).hasCauseThat().isEqualTo(credentialFailure);
  }

  @Test
  public void testDecryptWithCompletionExceptionCause_translatedToGeneralSecurityException()
      throws Exception {
    GeneralSecurityException credentialFailure =
        new GeneralSecurityException("credential refresh failed");
    KmsClient kms = new ThrowingKmsClient(new CompletionException(credentialFailure));
    Aead aead = new AwsKmsAead(kms, KEY_ARN);

    GeneralSecurityException thrown =
        assertThrows(
            GeneralSecurityException.class,
            () -> aead.decrypt(Random.randBytes(20), Random.randBytes(20)));

    assertThat(thrown).hasCauseThat().isEqualTo(credentialFailure);
  }

  @Test
  public void
      testEncryptWithCompletionExceptionUnrelatedCheckedCause_translatedToGeneralSecurityException()
          throws Exception {
    java.io.IOException unrelatedFailure = new java.io.IOException("unrelated checked failure");
    KmsClient kms = new ThrowingKmsClient(new CompletionException(unrelatedFailure));
    Aead aead = new AwsKmsAead(kms, KEY_ARN);

    GeneralSecurityException thrown =
        assertThrows(
            GeneralSecurityException.class,
            () -> aead.encrypt(Random.randBytes(20), Random.randBytes(20)));

    assertThat(thrown).hasCauseThat().isEqualTo(unrelatedFailure);
  }

  @Test
  public void testEncryptWithCompletionExceptionRuntimeExceptionCause_propagatedUnchanged()
      throws Exception {
    CompletionException exception = new CompletionException(new IllegalStateException("bug"));
    KmsClient kms = new ThrowingKmsClient(exception);
    Aead aead = new AwsKmsAead(kms, KEY_ARN);

    CompletionException thrown =
        assertThrows(
            CompletionException.class,
            () -> aead.encrypt(Random.randBytes(20), Random.randBytes(20)));

    assertThat(thrown).isSameInstanceAs(exception);
  }

  @Test
  public void testDecryptWithCompletionExceptionRuntimeExceptionCause_propagatedUnchanged()
      throws Exception {
    CompletionException exception = new CompletionException(new IllegalStateException("bug"));
    KmsClient kms = new ThrowingKmsClient(exception);
    Aead aead = new AwsKmsAead(kms, KEY_ARN);

    CompletionException thrown =
        assertThrows(
            CompletionException.class,
            () -> aead.decrypt(Random.randBytes(20), Random.randBytes(20)));

    assertThat(thrown).isSameInstanceAs(exception);
  }

  @Test
  public void testEncryptWithCompletionExceptionErrorCause_propagatedUnchanged() throws Exception {
    CompletionException exception = new CompletionException(new AssertionError("fatal"));
    KmsClient kms = new ThrowingKmsClient(exception);
    Aead aead = new AwsKmsAead(kms, KEY_ARN);

    CompletionException thrown =
        assertThrows(
            CompletionException.class,
            () -> aead.encrypt(Random.randBytes(20), Random.randBytes(20)));

    assertThat(thrown).isSameInstanceAs(exception);
  }

  @Test
  public void testDecryptWithCompletionExceptionErrorCause_propagatedUnchanged() throws Exception {
    CompletionException exception = new CompletionException(new AssertionError("fatal"));
    KmsClient kms = new ThrowingKmsClient(exception);
    Aead aead = new AwsKmsAead(kms, KEY_ARN);

    CompletionException thrown =
        assertThrows(
            CompletionException.class,
            () -> aead.decrypt(Random.randBytes(20), Random.randBytes(20)));

    assertThat(thrown).isSameInstanceAs(exception);
  }

  @Test
  public void testEncryptWithCauselessCompletionException_propagatedUnchanged() throws Exception {
    CompletionException exception = new CompletionException("no cause", null);
    KmsClient kms = new ThrowingKmsClient(exception);
    Aead aead = new AwsKmsAead(kms, KEY_ARN);

    CompletionException thrown =
        assertThrows(
            CompletionException.class,
            () -> aead.encrypt(Random.randBytes(20), Random.randBytes(20)));

    assertThat(thrown).isSameInstanceAs(exception);
  }

  @Test
  public void testDecryptWithCauselessCompletionException_propagatedUnchanged() throws Exception {
    CompletionException exception = new CompletionException("no cause", null);
    KmsClient kms = new ThrowingKmsClient(exception);
    Aead aead = new AwsKmsAead(kms, KEY_ARN);

    CompletionException thrown =
        assertThrows(
            CompletionException.class,
            () -> aead.decrypt(Random.randBytes(20), Random.randBytes(20)));

    assertThat(thrown).isSameInstanceAs(exception);
  }

  /**
   * A fake {@link KmsClient} whose {@code encrypt}/{@code decrypt} always throw a given {@link
   * CompletionException}, simulating what the AWS SDK's internal synchronous credential resolution
   * does when the configured credentials provider fails with a checked exception.
   */
  private static final class ThrowingKmsClient implements KmsClient {
    private final CompletionException exception;

    ThrowingKmsClient(CompletionException exception) {
      this.exception = exception;
    }

    @Override
    public EncryptResponse encrypt(EncryptRequest request) {
      throw exception;
    }

    @Override
    public DecryptResponse decrypt(DecryptRequest request) {
      throw exception;
    }

    @Override
    public String serviceName() {
      return "kms";
    }

    @Override
    public void close() {}
  }
}
