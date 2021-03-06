import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.RSAPrivateKeySpec;
import java.util.Arrays;
import java.util.Scanner;

public class Receiver {
	
	static String IV = "AAAAAAAAAAAAAAAA";
	static String symmetricKey;
	
	public static void main(String[] args)
	throws Exception {
		
		byte[] rcvHash;
		byte[] calcHash;
		
		PrivateKey privKey = readPrivKeyFromFile("YPrivate.key");
		//Get the symmetric key from file
		BufferedReader br = new BufferedReader(new FileReader("Symmetric.key"));
		symmetricKey = br.readLine();
		//get user input for message file
		Scanner input = new Scanner(System.in);
		System.out.print("Enter a name for the output file: ");
		String userInput = input.nextLine();

		long start, stop;

		// Decrypt the received hash
		start = System.nanoTime();
		rcvHash =  decrypt(userInput, privKey);
		stop = System.nanoTime();
		System.out.println("Time Elapsed During Decryption: " + ((stop - start) / 1000000) + " milliseconds.\n");

		// Locally calculate the hash
		start = System.nanoTime();
		calcHash = getDigitalDigest(userInput);
		stop = System.nanoTime();
		System.out.println("Time Elapsed During Local Calculation: " + ((stop - start) / 1000000) + " milliseconds.\n");

		//Compare the received hash with the locally calculated hash
		if(Arrays.equals(rcvHash, calcHash)){
			System.out.println("**************** HASH AUTHENTICATED ****************");
		}
		else{
			System.out.println("*************** HASHES DO NOT MATCH ***************");
		}
		
	}
	
	public static byte[] getDigitalDigest(String fileName) throws Exception {
		BufferedInputStream inputFile = new BufferedInputStream(new FileInputStream(fileName));
		//gets the estimated number of bytes in the message file
		int size = inputFile.available();
		
		byte[] msgArray = new byte[size];
		//write the message files byte representation to the msgArray
		inputFile.read(msgArray,0, size);
		inputFile.close();
		
		//create the message digest using the input message
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		DigestInputStream in = new DigestInputStream(inputFile, md);
		md = in.getMessageDigest();
		in.close();
		
		//Saves the hash value of the message digest to hash
		// This will always have a size of 32 bytes
		byte[] hash = md.digest();
		
		//Print the hash value to console in hex
		System.out.println("Calculated Digital Digest (Hash Value) of M:");
		toHex(hash);
		
		return hash;
	}
	
	public static byte[] decrypt(String userInput, PrivateKey privKey)
	throws NoSuchPaddingException, NoSuchAlgorithmException,
		   InvalidKeyException, IOException, BadPaddingException,
		   IllegalBlockSizeException, NoSuchProviderException,
		   InvalidAlgorithmParameterException {
		Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		rsaCipher.init(Cipher.DECRYPT_MODE, privKey );
		
		//pull input from the message.rsacipher and create output stream for rsa result
		BufferedInputStream inputFile = new BufferedInputStream(new FileInputStream("message.rsacipher"));
		BufferedOutputStream RSAout = new BufferedOutputStream(new FileOutputStream("message.received-msg"));
		int size = inputFile.available(); //uncomment to check how many bytes message.rsacipher is
		System.out.println("RSA cipher input size: " + size);
		//create array to store 117 byte piece of message.add-msg
		byte[] piece = new byte[128];
		long position = 0;
		int i;
		do {
			i = inputFile.read(piece,0, 128);
			// if the piece that was read was a full 128 bytes
			if (i == 128){
				//write that piece to message.received-msg
				RSAout.write(rsaCipher.doFinal(piece));
			}
		} while (i == 128); //if the piece wasn't a full 117 then its the end of the input file
		inputFile.close();
		RSAout.close();
		System.out.println("Saved RSA  decrypted cipher to message.received-msg\n");
		
		//************************* AES *************************************
		
		inputFile = new BufferedInputStream(new FileInputStream("message.received-msg"));
		//Stream to save decrypted message to user inputed file
		BufferedOutputStream msgOut = new BufferedOutputStream(new FileOutputStream(userInput));
		size = inputFile.available();
		System.out.println("AES cipher size: " + size);
		size = size - 32;
		byte[] hash = new byte[32];
		byte[] out = new byte[size];
		//read the first 32 bytes which is the hash and output
		inputFile.read(hash,0, 32);
		//read and output the original message
		inputFile.read(out,0, size);
		msgOut.write(out);
		System.out.println("Saved hash to PLACEHOLDER\n");
		//close all the streams
		inputFile.close();
		msgOut.close();
		
		//new cipher that uses AES encryption with CBC scheme and no padding
		Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding", "SunJCE");
		SecretKeySpec key = new SecretKeySpec(symmetricKey.getBytes("UTF-8"), "AES");
		//IvParameterSpec constrains the thing being encrypted to be a multiple of 16 bytes
		cipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(IV.getBytes("UTF-8")));
		//perform the decryption on the digital digest hash value which is always 32 bytes
		byte[] decryptedHash = cipher.doFinal(hash);
		System.out.println("Digital Digest (Hash Value) of M:");
		toHex(decryptedHash);
		BufferedOutputStream digest = new BufferedOutputStream(new FileOutputStream("message.hash-output"));
		digest.write(decryptedHash);
		digest.close();
		System.out.println("Saved hash to message.dd\n");
		return decryptedHash;
		
	}
	
	static void toHex(byte[] byteArray) {
		for (int k=0, j=0; k<byteArray.length; k++, j++) {
			System.out.format("%2X ", byteArray[k]) ;
			if (j >= 15) {
				System.out.println("");
				j=-1;
			}
		}
		System.out.println("");
	}
	
	public static PrivateKey readPrivKeyFromFile(String keyFileName)
	throws IOException {
		
		ObjectInputStream oin = new ObjectInputStream(
		 new BufferedInputStream(new FileInputStream(keyFileName)));
		
		try {
			BigInteger m = (BigInteger) oin.readObject();
			BigInteger e = (BigInteger) oin.readObject();
			
			System.out.println("Read from " + keyFileName + ": modulus = " +
							   m.toString() + ", exponent = " + e.toString() + "\n");
			
			RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(m, e);
			KeyFactory factory = KeyFactory.getInstance("RSA");
			PrivateKey key = factory.generatePrivate(keySpec);
			
			return key;
		} catch (Exception e) {
			throw new RuntimeException("Spurious serialisation error", e);
		} finally {
			oin.close();
		}
	}
}
