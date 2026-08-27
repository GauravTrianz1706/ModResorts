package com.acme.modres.mbean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import com.acme.modres.mbean.reservation.ReservationList;
import com.acme.modres.util.JsonInputStream;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Utility class for I/O operations.
 *
 * Migrated from local file system / temporary directory usage to Amazon S3 for
 * durable, cloud-native storage. All temporary file operations that previously
 * relied on File.createTempFile() (ephemeral local /tmp) are replaced with
 * in-memory processing and S3 object storage, ensuring data survives container
 * restarts and enabling multi-instance access
 * (fixes cr-java-0062 Local File System Write Operations,
 *  cr-java-0112 Local Temporary Storage Reliance).
 */
public final class IOUtils {

  private static final Logger logger = Logger.getLogger(IOUtils.class.getName());

  // S3 bucket name read from environment variable for cloud-native configuration
  private static final String S3_BUCKET_NAME = System.getenv("S3_BUCKET_NAME") != null
      ? System.getenv("S3_BUCKET_NAME")
      : "modresorts-data";

  /**
   * Reads a classpath resource into a byte array.
   * Replaces File.createTempFile() + FileOutputStream pattern with in-memory
   * processing — no ephemeral local /tmp dependency.
   */
  public static byte[] getResourceBytes(String path) {
    try (InputStream initialStream = IOUtils.class.getClassLoader().getResourceAsStream(path)) {
      if (initialStream == null) {
        logger.warning("Resource not found on classpath: " + path);
        return null;
      }
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[4096];
      int bytesRead;
      while ((bytesRead = initialStream.read(chunk)) != -1) {
        buffer.write(chunk, 0, bytesRead);
      }
      return buffer.toByteArray();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Returns the raw bytes of reservations.json from the classpath.
   * Used by exportRevervations() to build the ZIP in memory without touching
   * the local file system.
   */
  public static byte[] getReservationJsonBytes() {
    return getResourceBytes("reservations.json");
  }

  /**
   * Uploads content to Amazon S3 instead of writing to a local temporary
   * directory, ensuring data durability across container restarts
   * (fixes cr-java-0062 and cr-java-0112).
   */
  public static void uploadToS3(String s3Key, byte[] content, String contentType) {
    try (S3Client s3Client = S3Client.builder().build()) {
      PutObjectRequest putRequest = PutObjectRequest.builder()
          .bucket(S3_BUCKET_NAME)
          .key(s3Key)
          .contentType(contentType)
          .build();
      s3Client.putObject(putRequest, RequestBody.fromBytes(content));
      logger.info("Uploaded to S3: s3://" + S3_BUCKET_NAME + "/" + s3Key);
    } catch (S3Exception e) {
      logger.severe("Failed to upload to S3: " + e.awsErrorDetails().errorMessage());
      e.printStackTrace();
    }
  }

  /**
   * Downloads content from Amazon S3.
   */
  public static byte[] downloadFromS3(String s3Key) {
    try (S3Client s3Client = S3Client.builder().build()) {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(S3_BUCKET_NAME)
          .key(s3Key)
          .build();
      try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest)) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        while ((bytesRead = s3Object.read(chunk)) != -1) {
          buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
      }
    } catch (S3Exception | IOException e) {
      logger.severe("Failed to download from S3 key: " + s3Key);
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Parses ops.json from classpath using in-memory stream — no temp file needed.
   */
  public static OpMetadataList getOpListFromConfig() {
    byte[] data = getResourceBytes("ops.json");
    if (data == null) {
      return null;
    }
    try (JsonInputStream is = new JsonInputStream(data)) {
      OpMetadataList opList = (OpMetadataList) is.parseJsonAs(OpMetadataList.class);
      return opList;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Parses reservations.json from classpath using in-memory stream — no temp
   * file needed.
   */
  public static ReservationList getReservationListFromConfig() {
    byte[] data = getResourceBytes("reservations.json");
    if (data == null) {
      return null;
    }
    try (JsonInputStream is = new JsonInputStream(data)) {
      ReservationList reservationList = (ReservationList) is.parseJsonAs(ReservationList.class);
      return reservationList;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

}
