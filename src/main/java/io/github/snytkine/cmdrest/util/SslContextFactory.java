/*
 * Copyright 2026 - 2026 Dmitri Snytkine. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.snytkine.cmdrest.util;

import io.github.snytkine.cmdrest.model.KeystoreConfig;
import io.github.snytkine.cmdrest.model.SslConfig;
import io.github.snytkine.cmdrest.model.TruststoreConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import org.jspecify.annotations.Nullable;

/**
 * Builds a {@link SSLContext} from a rest-client's {@link SslConfig}, using only the JDK's built-in
 * cryptography so the application still compiles and runs as a GraalVM native image (no third-party
 * crypto dependency such as BouncyCastle).
 *
 * <p>Three configurations are supported:
 *
 * <ul>
 *   <li><b>skip-certificate-validation</b> — an all-trusting {@link X509ExtendedTrustManager} that
 *       disables certificate <em>and</em> hostname verification (the extended trust manager is the
 *       hook {@link java.net.http.HttpClient} uses for endpoint identification, so overriding it to
 *       do nothing also skips hostname checks).
 *   <li><b>truststore</b> — a custom trust store seeded with the certificate(s) from a PEM file, so
 *       a self-signed or private-CA server certificate is trusted.
 *   <li><b>keystore</b> — a key store holding a client certificate chain and (optionally) its PKCS#8
 *       private key, used to authenticate the client during a mutual-TLS handshake.
 * </ul>
 *
 * <p>Private keys must be in PKCS#8 PEM form ({@code -----BEGIN PRIVATE KEY-----} or, when
 * encrypted, {@code -----BEGIN ENCRYPTED PRIVATE KEY-----}). The legacy PKCS#1 form ({@code
 * -----BEGIN RSA PRIVATE KEY-----} / {@code -----BEGIN EC PRIVATE KEY-----}) is rejected with an
 * actionable message, since parsing it without a third-party library is not possible.
 *
 * <p>This class is a stateless utility: it holds no fields, exposes only static methods, and every
 * object it creates lives on the caller's stack, so it is inherently thread-safe.
 */
public final class SslContextFactory {

    /** Matches a single PEM block, capturing its type label and Base64 body. */
    private static final Pattern PEM_BLOCK =
            Pattern.compile("-----BEGIN ([A-Z0-9 ]+)-----(.+?)-----END \\1-----", Pattern.DOTALL);

    /** Private-key algorithms tried, in order, when decoding a PKCS#8 key of unknown type. */
    private static final List<String> KEY_ALGORITHMS = List.of("RSA", "EC", "DSA", "EdDSA");

    /** Utility class; not instantiable. */
    private SslContextFactory() {}

    /**
     * Builds an {@link SSLContext} for the given SSL configuration, or returns {@code null} when no
     * customization is required (a {@code null} config, or one that neither skips validation nor
     * declares a truststore or keystore).
     *
     * @param ssl the rest-client's SSL configuration, or {@code null}
     * @param suiteDir the directory of the test-suite file, used to resolve relative file paths; may
     *     be {@code null} when only absolute paths are used
     * @return a configured {@link SSLContext}, or {@code null} when the default TLS behavior suffices
     * @throws SslConfigurationException if the configuration references files that cannot be read or
     *     parsed, an unsupported key format, or an incorrect key password
     */
    public static @Nullable SSLContext create(@Nullable SslConfig ssl, @Nullable Path suiteDir) {
        if (ssl == null || !ssl.requiresCustomContext()) {
            return null;
        }
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            if (ssl.skip()) {
                context.init(null, new TrustManager[] {trustAllManager()}, new SecureRandom());
                return context;
            }
            KeyManager[] keyManagers = ssl.keystore() != null ? buildKeyManagers(ssl.keystore(), suiteDir) : null;
            TrustManager[] trustManagers =
                    ssl.truststore() != null ? buildTrustManagers(ssl.truststore(), suiteDir) : null;
            context.init(keyManagers, trustManagers, new SecureRandom());
            return context;
        } catch (SslConfigurationException e) {
            throw e;
        } catch (GeneralSecurityException | IOException e) {
            throw new SslConfigurationException("Failed to build SSL context: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves an SSL file path: absolute paths are used as-is, relative paths are resolved against
     * the test-suite directory (or the current working directory when {@code suiteDir} is {@code
     * null}).
     *
     * @param suiteDir the base directory for relative paths, or {@code null}
     * @param path the configured file path
     * @return the resolved {@link Path}
     */
    public static Path resolve(@Nullable Path suiteDir, String path) {
        Path p = Path.of(path);
        if (p.isAbsolute() || suiteDir == null) {
            return p;
        }
        return suiteDir.resolve(p);
    }

    /**
     * Builds the trust managers for a custom truststore by loading its PEM certificate(s) into a
     * fresh {@link KeyStore} and initializing a default {@link TrustManagerFactory}.
     *
     * @param truststore the truststore configuration
     * @param suiteDir the base directory for relative paths, or {@code null}
     * @return the trust managers to install in the {@link SSLContext}
     * @throws GeneralSecurityException if the trust store cannot be constructed
     * @throws IOException if the certificate file cannot be read
     */
    private static TrustManager[] buildTrustManagers(TruststoreConfig truststore, @Nullable Path suiteDir)
            throws GeneralSecurityException, IOException {
        String certPath = truststore.certificate();
        if (certPath == null || certPath.isBlank()) {
            throw new SslConfigurationException("ssl.truststore.certificate is required");
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        Collection<? extends Certificate> certs = loadCertificates(resolve(suiteDir, certPath));
        int index = 0;
        for (Certificate cert : certs) {
            trustStore.setCertificateEntry("cert-" + index++, cert);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

    /**
     * Builds the key managers for a client keystore (mTLS). The certificate chain is always loaded;
     * when a private key is supplied it is added as a key entry (so the client can authenticate),
     * otherwise the certificate is stored as a trusted-certificate entry.
     *
     * @param keystore the keystore configuration
     * @param suiteDir the base directory for relative paths, or {@code null}
     * @return the key managers to install in the {@link SSLContext}
     * @throws GeneralSecurityException if the key store cannot be constructed
     * @throws IOException if a certificate or key file cannot be read
     */
    private static KeyManager[] buildKeyManagers(KeystoreConfig keystore, @Nullable Path suiteDir)
            throws GeneralSecurityException, IOException {
        String certPath = keystore.certificate();
        if (certPath == null || certPath.isBlank()) {
            throw new SslConfigurationException("ssl.keystore.certificate is required");
        }
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        Certificate[] chain = loadCertificates(resolve(suiteDir, certPath)).toArray(new Certificate[0]);
        char[] password = keystore.password() != null ? keystore.password().toCharArray() : new char[0];

        String keyPath = keystore.privateKey();
        if (keyPath != null && !keyPath.isBlank()) {
            PrivateKey privateKey = loadPrivateKey(resolve(suiteDir, keyPath), keystore.password());
            keyStore.setKeyEntry("client", privateKey, password, chain);
        } else {
            int index = 0;
            for (Certificate cert : chain) {
                keyStore.setCertificateEntry("client-" + index++, cert);
            }
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        return kmf.getKeyManagers();
    }

    /**
     * Loads one or more X.509 certificates from a PEM file (a certificate chain is supported).
     *
     * @param path the certificate file
     * @return the parsed certificates, in file order
     * @throws GeneralSecurityException if the file does not contain valid X.509 certificates
     * @throws IOException if the file cannot be read
     */
    private static Collection<? extends Certificate> loadCertificates(Path path)
            throws GeneralSecurityException, IOException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream in = Files.newInputStream(path)) {
            Collection<? extends Certificate> certs = cf.generateCertificates(in);
            if (certs.isEmpty()) {
                throw new SslConfigurationException("No certificates found in: " + path);
            }
            return certs;
        } catch (java.security.cert.CertificateException e) {
            throw new SslConfigurationException(
                    "Could not parse certificate file '" + path + "': " + e.getMessage(), e);
        }
    }

    /**
     * Loads a PKCS#8 private key (encrypted or unencrypted) from a PEM file.
     *
     * @param path the private-key file
     * @param password the key password, or {@code null} when the key is not encrypted
     * @return the parsed {@link PrivateKey}
     * @throws GeneralSecurityException if the key cannot be decoded
     * @throws IOException if the file cannot be read
     */
    private static PrivateKey loadPrivateKey(Path path, @Nullable String password)
            throws GeneralSecurityException, IOException {
        String pem = Files.readString(path);
        Matcher m = PEM_BLOCK.matcher(pem);
        if (!m.find()) {
            throw new SslConfigurationException(
                    "Private key file '" + path + "' does not contain a PEM block (expected '-----BEGIN ... -----')");
        }
        String type = m.group(1).trim();
        byte[] der = Base64.getMimeDecoder().decode(m.group(2).replaceAll("\\s", ""));
        return switch (type) {
            case "PRIVATE KEY" -> decodePkcs8(new PKCS8EncodedKeySpec(der), path);
            case "ENCRYPTED PRIVATE KEY" -> decodeEncryptedPkcs8(der, password, path);
            case "RSA PRIVATE KEY", "EC PRIVATE KEY" ->
                throw new SslConfigurationException("Private key file '" + path + "' is in the legacy PKCS#1 ('" + type
                        + "') format, which is not supported. Convert it to PKCS#8 with: "
                        + "openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem");
            default ->
                throw new SslConfigurationException("Unsupported private key PEM type '" + type + "' in file: " + path);
        };
    }

    /**
     * Decodes an encrypted PKCS#8 private key using the supplied password.
     *
     * @param der the DER-encoded {@code EncryptedPrivateKeyInfo}
     * @param password the key password; must be non-null and non-blank
     * @param path the key file (for error messages)
     * @return the decrypted {@link PrivateKey}
     * @throws GeneralSecurityException if decryption or decoding fails
     * @throws IOException if the encrypted structure cannot be parsed
     */
    private static PrivateKey decodeEncryptedPkcs8(byte[] der, @Nullable String password, Path path)
            throws GeneralSecurityException, IOException {
        if (password == null || password.isEmpty()) {
            throw new SslConfigurationException(
                    "Private key file '" + path + "' is encrypted; set ssl.keystore.password to decrypt it");
        }
        EncryptedPrivateKeyInfo encrypted = new EncryptedPrivateKeyInfo(der);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(encrypted.getAlgName());
        Key pbeKey = skf.generateSecret(new PBEKeySpec(password.toCharArray()));
        try {
            PKCS8EncodedKeySpec keySpec = encrypted.getKeySpec(pbeKey);
            return decodePkcs8(keySpec, path);
        } catch (java.security.InvalidKeyException e) {
            throw new SslConfigurationException(
                    "Could not decrypt private key file '" + path + "'; the ssl.keystore.password is likely incorrect");
        }
    }

    /**
     * Decodes a PKCS#8 key spec, trying each supported key algorithm in turn.
     *
     * @param keySpec the PKCS#8 encoded key spec
     * @param path the key file (for error messages)
     * @return the decoded {@link PrivateKey}
     * @throws SslConfigurationException if no supported algorithm can decode the key
     */
    private static PrivateKey decodePkcs8(PKCS8EncodedKeySpec keySpec, Path path) {
        for (String algorithm : KEY_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
            } catch (java.security.NoSuchAlgorithmException | InvalidKeySpecException ignored) {
                // Try the next algorithm.
            }
        }
        throw new SslConfigurationException(
                "Could not decode private key file '" + path + "'; expected an RSA, EC, DSA or EdDSA PKCS#8 key");
    }

    /**
     * Creates an {@link X509ExtendedTrustManager} that trusts every certificate and performs no
     * hostname verification. Used only when {@code skip-certificate-validation} is enabled.
     *
     * <p>Package-private so it can be unit-tested directly.
     *
     * @return an all-trusting trust manager
     */
    static X509ExtendedTrustManager trustAllManager() {
        return new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {}

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
