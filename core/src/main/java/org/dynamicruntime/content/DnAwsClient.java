package org.dynamicruntime.content;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.StringUtils;
import org.apache.commons.io.IOUtils;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.DnDateUtil;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnAwsClient {
    public class UploadJob {
        public final DnCxt cxt;
        public final String bucket;
        public final boolean isPublic;
        public int uploadCount;
        public int totalCount;

        public UploadJob(DnCxt cxt, String bucket, boolean isPublic) {
            this.cxt = cxt;
            this.bucket = bucket;
            this.isPublic = isPublic;
        }
    }

    public AmazonS3 s3;

    public void init() {
        s3 = AmazonS3ClientBuilder.defaultClient();
    }

    public DnContentData getContent(String bucket, String s3Path, boolean downloadContent)
            throws DnException {
        S3Object obj = null;
        ObjectMetadata metadata;

        String rptPath = String.format("s3:%s/%s", bucket, s3Path);
        try {
            if (downloadContent) {
                obj = s3.getObject(bucket, s3Path);
                metadata = obj.getObjectMetadata();
            } else {
                metadata = s3.getObjectMetadata(bucket, s3Path);
            }
        } catch (AmazonServiceException e) {
            int code = e.getStatusCode();
            if (code == 404 /* Http 404 not found */) {
                return null;
            }
            throw new DnException("Could not get content for " + rptPath + ".", e);
        }
        if (metadata == null) {
            return null;
        }
        Date modified = null;
        String cType = metadata.getContentType();
        String mimeType = (cType != null) ? StrUtil.getToNextIndex(cType, 0, ";") :
                "application/octet-stream";
        var userMetadata = metadata.getUserMetadata();
        if (userMetadata != null) {
            String lmStr = userMetadata.get("srcLastModified");
            modified = toOptDate(lmStr);
        }
        boolean isBinary = DnContentUtil.isBinary(mimeType);
        String strContent = null;
        byte[] byteContent = null;
        if (obj != null) {
            // Using Java's "try with resources"
            try (var in = obj.getObjectContent()) {
                byte[] bytes = IOUtils.toByteArray(in);
                if (isBinary) {
                    byteContent = bytes;
                } else {
                    strContent = new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new DnException("Failed to read input stream from " + rptPath + ".", e,
                        DnException.INTERNAL_ERROR, DnException.NETWORK, DnException.IO);

            }
        }
        return new DnContentData(mimeType, isBinary, strContent, byteContent, modified);
    }

    public void uploadString(DnCxt cxt, String bucket, String s3Path, String content, boolean isPublic)
            throws DnException {
        String rptPath = String.format("s3:%s/%s", bucket, s3Path);
        var metadata = createMetadata(s3Path, cxt.now());
        byte[] contentBytes = content.getBytes(StringUtils.UTF8);

        InputStream is = new ByteArrayInputStream(contentBytes);
        metadata.setContentLength(contentBytes.length);
        var pr = new PutObjectRequest(bucket, s3Path, is, metadata);
        if (isPublic) {
            pr = pr.withCannedAcl(CannedAccessControlList.PublicRead);
        }
        try {
            s3.putObject(pr);
        } catch (SdkClientException e) {
            throw new DnException("Failed to send string content to " + rptPath + ".", e,
                    DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.IO);

        }
        LogContent.log.info(cxt, String.format("Uploaded string content to %s.", rptPath));
    }

    public void doUpload(DnCxt cxt, String bucket, String uploadDir, String s3Dir, boolean isPublic) throws DnException {
        String keyPrefix = (s3Dir.endsWith("/")) ? s3Dir : s3Dir + "/";
        File uploadFileDir = new File(uploadDir);
        if (!uploadFileDir.isDirectory()) {
            throw new DnException(String.format("Path %s to upload to S3 is not a directory.", uploadDir));
        }
        long startTime = System.currentTimeMillis();
        var uploadJob = new UploadJob(cxt, bucket, isPublic);
        doUploadImpl(uploadJob, uploadFileDir, keyPrefix, 0);
        long duration = System.currentTimeMillis() - startTime;
        LogContent.log.info(cxt, String.format("Uploaded %d of %d files from %s to s3:%s//%s in %d milliseconds.",
                uploadJob.uploadCount, uploadJob.totalCount, uploadDir, bucket, s3Dir, duration));
    }

    public void doUploadImpl(UploadJob uploadJob, File uploadDir, String keyPrefix, int nestLevel)
            throws DnException {
        if (nestLevel > 3) {
            throw new DnException("Nest level at " + uploadDir.getPath() + " was too deep to upload to S3.");
        }
        var files = uploadDir.list();
        if (files == null) {
            return;
        }
        for (var fileName : files) {
            if (!fileName.startsWith(".")) {
                File uFile = new File(uploadDir, fileName);
                if (uFile.isDirectory()) {
                    doUploadImpl(uploadJob, uFile, keyPrefix + fileName + "/", nestLevel + 1);
                } else {
                    if (uploadJob.totalCount >= 100) {
                        throw new DnException("Upload from " + uFile + " started sending too many files to S3.");
                    }
                    // Actually uploading file. Determine the metadata with the appropriate content type.
                    String s3Path = keyPrefix + fileName;
                    // See if file is already there.
                    Date lastModified = new Date(uFile.lastModified());
                    DnContentData existingData = getContent(uploadJob.bucket, s3Path, false);
                    String rptPath = String.format("s3:%s/%s", uploadJob.bucket, s3Path);
                    if (existingData != null && existingData.timestamp != null &&
                            existingData.timestamp.equals(lastModified)) {
                        LogContent.log.debug(uploadJob.cxt,
                                String.format("Skipping upload of %s to %s because file is already present.",
                                        uFile.getAbsolutePath(),
                                        rptPath));
                        uploadJob.totalCount++;
                        continue;
                    }

                    var metadata = createMetadata(s3Path, lastModified);
                    // We assume the content in the directory never changes. To change content,
                    // we publish to a new directory and point a yaml config file at the new location.
                    metadata.setCacheControl("public, immutable, max-age=3153600");

                    String verb = (existingData != null) ? "Upload replacing" : "Upload inserting";
                    LogContent.log.debug(uploadJob.cxt,
                            String.format("%s %s to %s.", verb, uFile.getAbsolutePath(),
                                    rptPath));
                    try {
                        // Build the upload package.
                        var pr = new PutObjectRequest(uploadJob.bucket, s3Path, uFile)
                                .withMetadata(metadata);
                        if (uploadJob.isPublic) {
                            pr = pr.withCannedAcl(CannedAccessControlList.PublicRead);
                        }
                        s3.putObject(pr);
                    } catch (SdkClientException e) {
                        throw new DnException("Failed to send " + uFile + " to " + rptPath + ".", e,
                                DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.IO);
                    }
                    uploadJob.uploadCount++;
                    uploadJob.totalCount++;
                }
            }
        }
    }

    public static ObjectMetadata createMetadata(String path, Date lastModified) {
        String mimeType = DnContentUtil.determineMimeType(path);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(mimeType);
        Map<String,String> userMetadata = mMapT("srcLastModified",
                DnDateUtil.formatDate(lastModified));
        metadata.setUserMetadata(userMetadata);
        return metadata;
    }
}
