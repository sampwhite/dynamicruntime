package org.dynamicruntime.content;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.dynamicruntime.config.ConfigLoadUtil;
import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.startup.ServiceInitializer;

import org.dynamicruntime.util.*;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnContentService implements ServiceInitializer {
    public static final String DN_CONTENT_SERVICE = DnContentService.class.getSimpleName();
    public static final String DN_USE_AWS_KEY = "portal.useAws";
    public static final String SITES_BUCKET_NAME = "dn-sites";
    public static final String SITE_ID = "siteId";
    public final ClassLoader classLoader = this.getClass().getClassLoader();
    public final DnAwsClient awsClient = new DnAwsClient();
    public boolean usingAws;
    public String dfltSiteId;
    public Parser mdParser;
    /** Markdown to HTML */
    public HtmlRenderer mdHtmlRenderer;
    /** FreeMarker templates to Strings */
    public DnTemplates templates;
    public String portalDir;
    public String siteDir;
    public DatedCacheMap<String,DnContentData> cachedSiteConfig = new DatedCacheMap<>(100);

    @Override
    public String getServiceName() {
        return DN_CONTENT_SERVICE;
    }

    public static DnContentService get(DnCxt cxt) {
        var obj = cxt.instanceConfig.get(DN_CONTENT_SERVICE);
        return (obj instanceof DnContentService) ? (DnContentService)obj : null;
    }

    public void onCreate(DnCxt cxt) {
        usingAws = DnConfigUtil.getConfigBool(cxt, DN_USE_AWS_KEY, false,
                "Whether to use AWS to server portal javascript, css, and images.");
        mdParser = Parser.builder().build();
        mdHtmlRenderer = HtmlRenderer.builder().build();
        templates = new DnTemplates();
        dfltSiteId = DnConfigUtil.getConfigString(cxt, "portal.defaultSiteId", "dn/current",
                "The siteId used to serve portal content when the *siteId* is not explicitly " +
                        "provided as a parameter.");
        portalDir = DnConfigUtil.getConfigString(cxt, "portal.resourceLocation",
                "./portal",
                "Directory or URL root of where top level portal content is returned, if not using AWS.");
        siteDir = DnConfigUtil.getConfigString(cxt, "sitesRoot.resourceLocation",
                portalDir,
                "Directory or URL root where static web resources are located if not using AWS. " +
                        "Resources referenced here are assumed to not change.");
        if (usingAws) {
            awsClient.init();
        }

    }

    @Override
    public void checkInit(DnCxt cxt) {

    }

    public DnContentData getPortalContent(DnCxt cxt, Map<String,Object> queryParams) throws DnException {
        String qSiteId = getOptStr(queryParams, SITE_ID);
        String siteId = (qSiteId != null) ? qSiteId : dfltSiteId;
        return cachedSiteConfig.getItem(siteId, 2, false,
                (existing) -> {
            String yamlFileName = siteId + ".yaml";
            Map<String,Object> yamlParams;
            if (usingAws) {
                DnContentData yamlData = awsClient.getContent(SITES_BUCKET_NAME, yamlFileName, true);
                if (yamlData == null) {
                    throw DnException.mkFileIo(String.format("Cannot find site yaml fle %s in AWS bucket %s.",
                            yamlFileName, SITES_BUCKET_NAME), null, DnException.NOT_FOUND);
                }
                // First see if we can reuse content.
                if (existing != null && existing.item != null && yamlData.timestamp != null &&
                    yamlData.timestamp.equals(existing.item.timestamp)) {
                    return existing.item;
                }
                try {
                    yamlParams = ConfigLoadUtil.parseYamlText(yamlData.strContent);
                    String entryPoint = getReqStr(yamlParams, "site.entryPoint");
                    DnContentData data = awsClient.getContent(SITES_BUCKET_NAME, entryPoint, true);
                    if (data == null) {
                        throw new DnException(String.format("Could not retrieve data from AWS at key %s that " +
                                        "was retrieved from AWS file %s.", entryPoint, yamlFileName),
                                DnException.NOT_FOUND);
                    }
                    // Use timestamp of config file as timestamp of index page that we retrieved. This way
                    // we can test against it for the caching.
                    data.timestamp = yamlData.timestamp;
                    return data;
                } catch (IOException e) {
                    throw new DnException("Could not parse yaml file " + yamlFileName + " with content " +
                            yamlData.strContent);
                }
            } else {
                /* Get file locally. Useful for tests and in-memory deployment. */
                File portalFile = new File(portalDir, yamlFileName);
                if (!portalFile.exists()) {
                    throw DnException.mkFileIo(String.format("Cannot find portal configuration file %s.",
                            portalFile.getAbsolutePath()), null, DnException.NOT_FOUND);
                }
                yamlParams = ConfigLoadUtil.parseYaml(portalFile);
                // The config file tells us where to find our website.
                String entryPoint = getReqStr(yamlParams, "site.entryPoint");
                File f = new File(portalDir, entryPoint);
                if (!f.exists()) {
                    throw DnException.mkFileIo(String.format("Cannot find portal entry point %s.", f.getAbsolutePath()), null,
                            DnException.NOT_FOUND);
                }
                return getContentForFile(cxt, f);
            }
        });
    }

    public DnContentData getSiteContent(DnCxt cxt, String relativePath) throws DnException {
        File f = new File(siteDir, relativePath);
        if (!f.exists()) {
            throw DnException.mkFileIo(String.format("Cannot find site resource %s.", f.getAbsolutePath()), null,
                    DnException.NOT_FOUND);
        }
        DnContentData retVal = getContentForFile(cxt, f);

        if (relativePath.contains("static")) {
            retVal.immutable = true;
        }
        return retVal;
    }

    public DnContentData getContent(DnCxt cxt, String resourcePath) throws DnException {
        File resource = getFileResource(resourcePath);
        return getContentForFile(cxt, resource);
    }

    public DnContentData getContentForFile(DnCxt cxt, File resource) throws DnException {
        String resourcePath = resource.getPath();
        Date resTimestamp = new Date(resource.lastModified());
        DnContentData templatedData = getTemplateContent(cxt, resourcePath, resource,
                mMap());
        if (templatedData != null) {
            return templatedData;
        }

        String mimeType = DnContentUtil.determineMimeType(resourcePath);
        boolean isBinary = mimeType.startsWith("image") || mimeType.startsWith("video") ||
                mimeType.startsWith("audio");
        if (isBinary) {
            var bytes = IoUtil.readInBinaryFile(resource);
            return new DnContentData(mimeType, true, null, bytes, resTimestamp);
        } else {
            String resp = IoUtil.readInFile(resource);
            return new DnContentData(mimeType, false, resp, null, resTimestamp);
        }
    }

    public DnContentData getTemplateContent(@SuppressWarnings("unused") DnCxt cxt, String resourcePath,
            File f, Map<String,Object> args) throws DnException {
        if (f == null) {
            f = getFileResource(resourcePath);
        }
        String ext = StrUtil.getAfterLastIndex(resourcePath, ".");
        if ("md".equals(ext)) {
            DnTemplates.DnTemplate t = templates.checkGetTemplate(resourcePath, f, (content -> {
                Node node = mdParser.parse(content);
                Node child = node.getFirstChild();
                Node childNext = child != null ? child.getFirstChild() : null;
                while (childNext != null && !(child instanceof Text)) {
                    child = childNext;
                    childNext = childNext.getNext();
                }
                String title = (child instanceof Text) ? ((Text)child).getLiteral() : "Dynamic Runtime";
                String body = mdHtmlRenderer.render(node);
                // Return null for actual content output so we do not generate actual FreeMarker template.
                return new DnTemplates.DnOutput(null, mMap("title", title, "body", body));
            }));
            // Eventually we may augment *baseParams*.
            return applyHtmlLayout(cxt, t.baseParams);
        }
        if ("html".equals(ext) || "ftl".equals(ext)) {
            DnTemplates.DnTemplate t = templates.checkGetTemplate(resourcePath, f, (content -> {
                String head = "html".equals(ext) ?
                        PageUtil.extractSection(content, "<!--B-HEAD-->", "<!--E-HEAD-->") : null;
                String body = (head != null) ?
                        PageUtil.extractSection(content, "<!--B-BODY-->", "<!--E-BODY-->") : null;
                if (body != null) {
                    // Page is turned into arguments to the layout.ftl page.
                    return new DnTemplates.DnOutput(null, mMap("head", head, "body", body));
                }
                // Evaluate HTML or FTL page without applying layout.
                return new DnTemplates.DnOutput(content, args);
            }));
            if (t.template != null) {
                return DnContentData.mkHtml(templates.evalTemplate(t, args).output);
            } else {
                return applyHtmlLayout(cxt, t.baseParams);
            }
        }
        return null;
    }



    public DnContentData applyHtmlLayout(DnCxt cxt, Map<String,Object> baseParams) throws DnException {
        var layoutFile = getFileResource("layout/layout.ftl");
        DnTemplates.DnTemplate layoutTemplate = templates.checkGetTemplate("layout/layout.ftl",
                layoutFile, null);
        var params = cloneMap(baseParams);
        if (cxt.userProfile != null) {
            String name = isEmpty(cxt.userProfile.publicName) ? cxt.userProfile.authId : cxt.userProfile.publicName;
            if (name != null) {
                params.put("username", name);
            }
        }

        var output = templates.evalTemplate(layoutTemplate, params);
        return DnContentData.mkHtml(output.output);
    }

    public File getFileResource(String resourcePath) throws DnException {
        var resourceUrl = classLoader.getResource(resourcePath);
        if (resourceUrl == null) {
            throw DnException.mkFileIo(String.format("Cannot find resource %s.", resourcePath), null,
                    DnException.NOT_FOUND);
        }
        return new File(resourceUrl.getFile());
    }
}
