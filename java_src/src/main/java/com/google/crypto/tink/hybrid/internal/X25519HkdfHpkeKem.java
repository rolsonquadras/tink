// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
///////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.hybrid.internal;

import com.google.crypto.tink.subtle.Bytes;
import com.google.crypto.tink.subtle.Hkdf;
import com.google.crypto.tink.subtle.X25519;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;

/** Diffie-Hellman-based HPKE KEM variant with X25519 and HKDF. */
public final class X25519HkdfHpkeKem implements HpkeKem {
  private static final byte[] X25519_HKDF_SHA256_KEM_ID = HpkeUtil.intToByteArray(2, 0x20);

  private final String macAlgorithm;

  /** Construct X25519-HKDF HPKE KEM using {@code macAlgorithm}. */
  public X25519HkdfHpkeKem(String macAlgorithm) {
    this.macAlgorithm = macAlgorithm;
  }

  private byte[] deriveKemSharedSecret(
      byte[] dhSharedSecret, byte[] senderPublicKey, byte[] recipientPublicKey)
      throws GeneralSecurityException {
    byte[] kemContext = Bytes.concat(senderPublicKey, recipientPublicKey);
    int macLength = Mac.getInstance(macAlgorithm).getMacLength();
    return Hkdf.computeHkdf(
        macAlgorithm,
        HpkeUtil.labelIkm("eae_prk", dhSharedSecret, X25519_HKDF_SHA256_KEM_ID),
        /*salt=*/ null,
        HpkeUtil.labelInfo("shared_secret", kemContext, X25519_HKDF_SHA256_KEM_ID, macLength),
        macLength);
  }

  /** Helper function factored out to facilitate unit testing. */
  HpkeKemEncapOutput encapsulate(byte[] recipientPublicKey, byte[] senderPrivateKey)
      throws GeneralSecurityException {
    byte[] dhSharedSecret = X25519.computeSharedSecret(senderPrivateKey, recipientPublicKey);
    byte[] senderPublicKey = X25519.publicFromPrivate(senderPrivateKey);
    byte[] kemSharedSecret =
        deriveKemSharedSecret(dhSharedSecret, senderPublicKey, recipientPublicKey);
    return new HpkeKemEncapOutput(kemSharedSecret, senderPublicKey);
  }

  @Override
  public HpkeKemEncapOutput encapsulate(byte[] recipientPublicKey) throws GeneralSecurityException {
    return encapsulate(recipientPublicKey, X25519.generatePrivateKey());
  }

  @Override
  public byte[] decapsulate(byte[] encapsulatedKey, byte[] recipientPrivateKey)
      throws GeneralSecurityException {
    byte[] dhSharedSecret = X25519.computeSharedSecret(recipientPrivateKey, encapsulatedKey);
    byte[] recipientPublicKey = X25519.publicFromPrivate(recipientPrivateKey);
    return deriveKemSharedSecret(dhSharedSecret, encapsulatedKey, recipientPublicKey);
  }
}
