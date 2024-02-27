package it.zwets.sms.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encryption and decryption using public and private keys.
 * 
 * Plaintext is encrypted with a randomly generated secret symmetric key.
 * This key is encrypted with a public key and prepended to the ciphertext.
 * 
 * Upon decryption, the prepended key is read and decrypted with the private
 * key, and then used to decrypt the actual ciphertext.
 *
 * The crypto algorithms and parameters are the same as used in OdkCrypto.
 * The difference is that ODK uses an additional parameter (instance ID),
 * and transfers the PKI encrypted key separately from the ciphertext.
 */
public class PkiCrypto {
    
    private static Logger LOG = LoggerFactory.getLogger(PkiCrypto.class);

    // Byte size of the generated symmetric key and its encryption
    public static final int KEY_SIZE = 256 / 8; // for AES
    public static final int ENC_KEY_SIZE = 256; // for RSA with SHA256
    
    // Parameters for the RSA encryption of the symmetric secret
    private static final String ASYMMETRIC_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final AlgorithmParameterSpec ASYMMETRIC_PARAMETERS = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    // Parameters for the symmetric encryption
    private static final String SYMMETRIC_ALGORITHM = "AES/CFB/PKCS5Padding";
    private static final String SYMMETRIC_KEYTYPE = "AES";
    private static final int IV_LENGTH = 16;
    
    private static SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * Encrypt plaintext with a public key.
     * 
     * @param pubkey public key for encrypting the random symmetric key
     * @param plaintext the bytes to encode
     * @return byte array with the encrypted symmetric key followed by the ciphertext
     */
    public static byte[] encrypt(final PublicKey pubkey, final byte[] plaintext) {
        return new Encryptor(pubkey).encrypt(plaintext);
    }

    /**
     * Decrypt ciphertext with a private key.
     * 
     * @param privkey the private key for decrypting the symmetric key
     * @param ciphertext the encrypted symmetric key followed by the ciphertext
     * @return the decrypted payload
     */
    public static byte[] decrypt(final PrivateKey privkey, final byte[] ciphertext) {
        return new Decryptor(privkey).decrypt(ciphertext);
    }

    /**
     * Encryptor.
     */
    public static final class Encryptor {

        private final PublicKey pubkey;

        /**
         * Create an encryptor for the specified public key.
         * @param pubkey
         */
        public Encryptor(PublicKey pubkey) {
            this.pubkey = pubkey;
        }

        /**
         * Encrypt plaintext from is to ciphertext on os.
         * 
         * The ciphertext is preceded ny ENC_KEY_SIZE bytes that hold the
         * public key encrypted symmetric key to decrypt the remaining bytes.
         * 
         * @param is an open {@link OutputStream}
         * @param os an open {@link InputStream}
         */
        public void encrypt(InputStream is, OutputStream os) {
            LOG.debug("Encrypting input stream");

            // Generate a new random key of KEY_SIZE
            byte[] key = new byte[KEY_SIZE];
            SECURE_RANDOM.nextBytes(key);

            // Encrupt the key with public key
            byte[] encKey = pkiEncrypt(pubkey, key);
            assert encKey.length == ENC_KEY_SIZE;

            CipherOutputStream cos = null;
            try {
                // Write the encrypted key to the output
                os.write(encKey);

                // Write the encypted payload to the output
                Cipher c = getSymmetricCipher(Cipher.ENCRYPT_MODE, key);
                cos = new CipherOutputStream(os, c);
                is.transferTo(cos);
                cos.close();
            }
            catch (IOException e) {
                throw new RuntimeException("Failed to encrypt stream: %s".formatted(e.getMessage()), e);
            }
            finally {
                if (cos != null) try { cos.close(); } catch (IOException e) { /* ignore */ }
            }

        }
        
        /**]
         * Encrypt plaintext to ciphertext
         * 
         * @param plaintext the payload to encode
         * @return the pk-encrypted key followed by the actual ciphertext
         */
        public byte[] encrypt(final byte[] plaintext) {
            ByteArrayInputStream bis = new ByteArrayInputStream(plaintext);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            encrypt(bis, bos);
            return bos.toByteArray();
        }
    };

    /**
     * Decryptor.
     */
    public static final class Decryptor {

        private final PrivateKey privateKey;

        /**
         * Create decryptor for the given privkey.
         * @param privkey the private key of the reciptient
         */
        public Decryptor(final PrivateKey privkey) {
            this.privateKey = privkey;
        }

        /**
         * Decrypt ciphertext from is to plaintext on os.
         *
         * @param is an open {@link InputStream}
         * @param os an open {@link OutputStream}
         */
        public void decrypt(InputStream is, OutputStream os) {
            LOG.debug("Decrypting input stream");
            
            CipherInputStream cis = null;
            try {
                // Read and decrypt the encrypted symmetric key
                byte[] encKey = is.readNBytes(ENC_KEY_SIZE);
                byte[] key = pkiDecrypt(privateKey, encKey);

                // Decrypt the remainder of the stream
                Cipher c = getSymmetricCipher(Cipher.DECRYPT_MODE, key);
                cis = new CipherInputStream(is, c);
                cis.transferTo(os);
                cis.close();
            }
            catch (IOException e) {
                throw new RuntimeException("Failed to decrypt the ciphertext: %s".formatted(e.getMessage()), e);
            }
            finally {
                if (cis != null) try { cis.close(); } catch (IOException e) { /* ignore */ }
            }
        }
        
        /**
         * Decrypt ciphertext to plaintext..
         * 
         * @param ciphertext the payload to decrypt
         * @return plaintext
         */
        public byte[] decrypt(final byte[] ciphertext) {
            ByteArrayInputStream bis = new ByteArrayInputStream(ciphertext);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            decrypt(bis, bos);
            return bos.toByteArray();
        }
    };

    /**
     * Helper to encrypt (a limited amount of) plaintext with a public key.
     * @param key the public key
     * @param plaintext the input
     * @return the ciphertext
     */
    private static byte[] pkiEncrypt(final PublicKey key, final byte[] plaintext)
    {
        try {
            Cipher cipher = Cipher.getInstance(ASYMMETRIC_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, ASYMMETRIC_PARAMETERS);
            return cipher.doFinal(plaintext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("PKI error during encryption: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * Helper to decrypt a ciphertext with a private key.
     * @param key the private key
     * @param ciphertext the input
     * @return the plaintext
     */
    private static byte[] pkiDecrypt(final PrivateKey key, final byte[] ciphertext) 
    {
        try {
            Cipher cipher = Cipher.getInstance(ASYMMETRIC_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, ASYMMETRIC_PARAMETERS);
            return cipher.doFinal(ciphertext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("PKI error during decryption: %s".formatted(e.getMessage()), e);
        }
    }
    
    /**
     * Generates a new symmetric cipher based on key material.
     * @param mode Cipher.ENCRYPT or Cipher.DECRIPT
     * @param key the key material
     * @return the initialised symmetic cipher
     */
    private static Cipher getSymmetricCipher(int mode, final byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance(SYMMETRIC_ALGORITHM);
            cipher.init(mode,
                    new SecretKeySpec(key, SYMMETRIC_KEYTYPE), 
                    new IvParameterSpec(makeIV(key)));
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Failed to create symmetric cipher: %s".formatted(e.getMessage()), e);
        }
    }
    
    /**
     * Creates an algorithm Initialisation Vector based on key material
     * @param key the key material
     * @return the IV_LENGTH sized IV
     */
    private static final byte[] makeIV(final byte[] key) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            
            // Compute the MD5 of the key
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(key);
            byte[] md5 = md.digest();
    
            // Fill the IV with the MD5
            for (int i = 0; i < IV_LENGTH; ++i) {
                iv[i] = md5[i % md5.length];
            }
            
            return iv;
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to create IV: %s".formatted(e.getMessage()), e);
        }
    }
}
