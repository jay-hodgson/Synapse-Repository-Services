package org.sagebionetworks;

import static org.junit.Assert.assertTrue;

import org.sagebionetworks.repo.manager.S3TestUtils;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;

/*
 * The methods in this class help read and validate emails (written as files when testing).
 */
public class EmailValidationUtil {
	
	public static boolean doesFileExist(String key, long maxWaitTimeInMillis) throws Exception {
		AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(
				StackConfiguration.getIAMUserId(), StackConfiguration.getIAMUserKey()));
		
		return S3TestUtils.doesFileExist(StackConfiguration.getS3Bucket(), key, s3Client, maxWaitTimeInMillis);
	}
	
	public static void deleteFile(String key) throws Exception {
		AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(
				StackConfiguration.getIAMUserId(), StackConfiguration.getIAMUserKey()));
		
		S3TestUtils.deleteFile(StackConfiguration.getS3Bucket(), key, s3Client);
	}
	
	public static String readFile(String key) throws Exception {
		AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(
				StackConfiguration.getIAMUserId(), StackConfiguration.getIAMUserKey()));
		
		return S3TestUtils.getObjectAsString(StackConfiguration.getS3Bucket(), key, s3Client);
	}
	
	public static String getBucketKeyForEmail(String email) {
		assertTrue(email!=null && email.length()>0);
		return  email+".json";
	}
	
	public static String getTokenFromFile(String key, String startString, String endString) throws Exception {
		// the email is written to a local file.  Read it and extract the link
		String body = EmailValidationUtil.readFile(key);
		int endpointIndex = body.indexOf(startString);
		assertTrue(endpointIndex>=0);
		int tokenStart = endpointIndex+startString.length();
		assertTrue(tokenStart>=0);
		int tokenEnd = body.indexOf(endString, tokenStart);
		assertTrue(tokenEnd>=0);
		String token = body.substring(tokenStart, tokenEnd);
		return token;
	}
}
