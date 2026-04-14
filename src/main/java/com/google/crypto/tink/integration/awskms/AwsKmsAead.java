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

import com.google.common.base.Splitter;
import com.google.crypto.tink.Aead;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.utils.BinaryUtils;

/**
 * A {@link Aead} that forwards encryption/decryption requests to a key in <a
 * href="https://aws.amazon.com/kms/">AWS KMS</a>.
 *
 * @since 1.0.0
 */
public final class AwsKmsAead implements Aead {

  /** This client knows how to talk to AWS KMS. */
  private final KmsClient kmsClient;

  // The location of a crypto key in AWS KMS, without the aws-kms:// prefix.
  private final String keyArn;

  public AwsKmsAead(KmsClient kmsClient, String keyArn) {
    this.kmsClient = kmsClient;
    this.keyArn = keyArn;
  }

  @Override
  public byte[] encrypt(final byte[] plaintext, final byte[] associatedData)
      throws GeneralSecurityException {
    try {
      EncryptRequest.Builder reqBuilder =
          EncryptRequest.builder().keyId(keyArn).plaintext(SdkBytes.fromByteArray(plaintext));

      if (associatedData != null && associatedData.length != 0) {
        Map<String, String> encryptionContext = new HashMap<>();
        encryptionContext.put("associatedData", BinaryUtils.toHex(associatedData));
        reqBuilder.encryptionContext(encryptionContext);
      }
      EncryptResponse response = kmsClient.encrypt(reqBuilder.build());
      return response.ciphertextBlob().asByteArray();
    } catch (SdkClientException | KmsException e) {
      throw new GeneralSecurityException("encryption failed", e);
    }
  }

  @Override
  public byte[] decrypt(final byte[] ciphertext, final byte[] associatedData)
      throws GeneralSecurityException {
    try {
      DecryptRequest.Builder reqBuilder =
          DecryptRequest.builder().keyId(keyArn).ciphertextBlob(SdkBytes.fromByteArray(ciphertext));

      if (associatedData != null && associatedData.length != 0) {
        Map<String, String> encryptionContext = new HashMap<>();
        encryptionContext.put("associatedData", BinaryUtils.toHex(associatedData));
        reqBuilder.encryptionContext(encryptionContext);
      }
      DecryptResponse result = kmsClient.decrypt(reqBuilder.build());
      // In AwsKmsAead.decrypt() it is important to check the returned KeyId against the one
      // previously configured. If we don't do this, the possibility exists for the ciphertext to
      // be replaced by one under a key we don't control/expect, but do have decrypt permissions
      // on.
      // The check is disabled if keyARN is not in key ARN format.
      // See https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html#key-id.
      if (isKeyArnFormat(keyArn) && !result.keyId().equals(keyArn)) {
        throw new GeneralSecurityException("decryption failed: wrong key id");
      }
      return result.plaintext().asByteArray();
    } catch (SdkClientException | KmsException e) {
      throw new GeneralSecurityException("decryption failed", e);
    }
  }

  /** Returns {@code true} if {@code keyArn} is in key ARN format. */
  private static boolean isKeyArnFormat(String keyArn) {
    List<String> tokens = Splitter.on(':').splitToList(keyArn);
    return tokens.size() == 6 && tokens.get(5).startsWith("key/");
  }
}
