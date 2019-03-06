package script

import org.dynamicruntime.content.DnAwsClient
import org.dynamicruntime.content.DnContentService
import org.dynamicruntime.context.DnCxt

class AwsUploadWebapp {
    DnAwsClient awsClient = new DnAwsClient()
    public static BUCKET_NAME = DnContentService.SITES_BUCKET_NAME

    static void main(String[] args) {
        if (!args || args.length < 3) {
            println("\n*** AwsUploadWebapp ***")
            println("Arguments are: <siteId> <dir-to-upload> <s3-target-dir>")
            return
        }

        new AwsUploadWebapp().doUpload(args[0], args[1], args[2])
    }

    AwsUploadWebapp() {
        awsClient.init()
    }

    def doUpload(String siteId, String uploadDir, String destDir) {
        def cxt = DnCxt.mkSimpleCxt("uploadToS3")
        String s3DestDir = (destDir.endsWith("/")) ? destDir : destDir + "/"

        // Publish the directory.
        awsClient.doUpload(cxt, BUCKET_NAME, uploadDir, s3DestDir, true)

        // Set up a yaml file to point to content we just published.
        def s3YamlPath = siteId + ".yaml"
        def content = "site.entryPoint: ${s3DestDir}index.html"
        awsClient.uploadString(cxt, BUCKET_NAME, s3YamlPath, content, true)

        println("***\nUploaded site ${siteId} from ${uploadDir} to s3:${BUCKET_NAME}/${s3DestDir}\n***")
    }

}
