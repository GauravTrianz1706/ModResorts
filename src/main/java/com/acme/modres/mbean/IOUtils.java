package com.acme.modres.mbean;

import java.io.IOException;
import java.io.InputStream;

import com.acme.modres.mbean.reservation.ReservationList;
import com.acme.modres.util.JsonInputStream;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * IOUtils - Cloud-native utility class that replaces local file system
 * operations with Amazon S3 for durable, scalable storage.
 *
 * Configuration is read from classpath resources (bundled in the JAR/WAR),
 * and any write/export operations use Amazon S3 instead of the local
 * temporary file system, eliminating ephemeral storage dependencies.
 */
public final class IOUtils {

  /** S3 bucket name resolved from environment variable for cloud portability. */
  private static final String S3_BUCKET_NAME = System.getenv("S3_BUCKET_NAME") != null
      ? System.getenv("S3_BUCKET_NAME")
      : "modresorts-data";

  /**
   * Reads a classpath resource and returns its raw bytes.
   * This replaces the previous pattern of writing to a local temp file
   * (File.createTempFile) and returning a java.io.File handle.
   *
   * @param path classpath-relative resource path (e.g. "reservations.json")
   * @return byte array of the resource content, or null on error
   */
  public static byte[] getResourceAsBytes(String path) {
    try (InputStream initialStream = IOUtils.class.getClassLoader().getResourceAsStream(path)) {
      if (initialStream == null) {
        return null;
      }
      return initialStream.readAllBytes();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Convenience method: returns the raw bytes of the reservations.json
   * classpath resource. Used by AvailabilityCheckerServlet for ZIP export.
   */
  public static byte[] getReservationDataAsBytes() {
    return getResourceAsBytes("reservations.json");
  }

  /**
   * Uploads arbitrary byte content to Amazon S3.
   * Replaces local FileOutputStream / temp-directory writes.
   *
   * @param key         S3 object key (path within the bucket)
   * @param data        content to upload
   * @param contentType MIME type of the content
   */
  public static void uploadToS3(String key, byte[] data, String contentType) {
    try (S3Client s3Client = S3Client.builder().build()) {
      PutObjectRequest putRequest = PutObjectRequest.builder()
          .bucket(S3_BUCKET_NAME)
          .key(key)
          .contentType(contentType)
          .build();
      s3Client.putObject(putRequest, RequestBody.fromBytes(data));
    }
  }

  /**
   * Downloads an object from Amazon S3 and returns its content as a byte array.
   *
   * @param key S3 object key
   * @return byte array of the object content, or null if not found
   */
  public static byte[] downloadFromS3(String key) {
    try (S3Client s3Client = S3Client.builder().build()) {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(S3_BUCKET_NAME)
          .key(key)
          .build();
      try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest)) {
        return s3Object.readAllBytes();
      }
    } catch (NoSuchKeyException e) {
      return null;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Reads the ops.json configuration from the classpath and parses it.
   * Uses try-with-resources to ensure the stream is always closed.
   */
  public static OpMetadataList getOpListFromConfig() {
    byte[] data = getResourceAsBytes("ops.json");
    if (data == null) {
      return null;
    }
    try (JsonInputStream is = new JsonInputStream(data)) {
      OpMetadataList opList = new OpMetadataList();
      opList = (OpMetadataList) is.parseJsonAs(OpMetadataList.class);
      return opList;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Reads the reservations.json configuration from the classpath and parses it.
   * Uses try-with-resources to ensure the stream is always closed.
   */
  public static ReservationList getReservationListFromConfig() {
    byte[] data = getResourceAsBytes("reservations.json");
    if (data == null) {
      return null;
    }
    try (JsonInputStream is = new JsonInputStream(data)) {
      ReservationList reservationList = new ReservationList();
      reservationList = (ReservationList) is.parseJsonAs(ReservationList.class);
      return reservationList;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

}
