package org.dynamicruntime.content;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.startup.ServiceInitializer;

import org.dynamicruntime.util.IoUtil;
import org.dynamicruntime.util.PageUtil;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.io.File;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.Date;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnContentService implements ServiceInitializer {
    public static final String DN_CONTENT_SERVICE = DnContentService.class.getSimpleName();
    ClassLoader classLoader;
    public FileNameMap fileNameMap;
    public Parser mdParser;
    /** Markdown to HTML */
    public HtmlRenderer mdHtmlRenderer;
    /** FreeMarker templates to Strings */
    public DnTemplates templates;


    @Override
    public String getServiceName() {
        return DN_CONTENT_SERVICE;
    }

    public static DnContentService get(DnCxt cxt) {
        var obj = cxt.instanceConfig.get(DN_CONTENT_SERVICE);
        return (obj instanceof DnContentService) ? (DnContentService)obj : null;
    }

    public void onCreate(DnCxt cxt) {
        classLoader = this.getClass().getClassLoader();
        fileNameMap = URLConnection.getFileNameMap();
        mdParser = Parser.builder().build();
        mdHtmlRenderer = HtmlRenderer.builder().build();
        templates = new DnTemplates();
    }

    @Override
    public void checkInit(DnCxt cxt) {

    }

    public DnContentData getContent(@SuppressWarnings("unused") DnCxt cxt, String resourcePath) throws DnException {
        File f = getFileResource(resourcePath);
        Date resTimestamp = new Date(f.lastModified());
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
            return applyHtmlLayout(t.baseParams);
        }
        if ("html".equals(ext)) {
            DnTemplates.DnTemplate t = templates.checkGetTemplate(resourcePath, f, (content -> {
                String head = PageUtil.extractSection(content, "<!--B-HEAD-->", "<!--E-HEAD-->");
                String body = (head != null) ?
                        PageUtil.extractSection(content, "<!--B-BODY-->", "<!--E-BODY-->") : null;
                if (body != null) {
                    // Page is turned into arguments to the layout.ftl page.
                    return new DnTemplates.DnOutput(null, mMap("head", head, "body", body));
                }
                // Evaluate HTML page without applying layout.
                return new DnTemplates.DnOutput(content, mMap());
            }));
            if (t.template != null) {
                return DnContentData.mkHtml(templates.evalTemplate(t, mMap()).output);
            } else {
                return applyHtmlLayout(t.baseParams);
            }
        }

        String mimeType = fileNameMap.getContentTypeFor(resourcePath);
        if (mimeType == null && resourcePath.endsWith(".ico")) {
            mimeType = "image/ico";
        }
        if (mimeType == null && resourcePath.endsWith(".js")) {
            mimeType = "application/javascript";
        }
        if (mimeType == null && resourcePath.endsWith(".css")) {
            mimeType = "text/css";
        }
        if (mimeType == null) {
            mimeType = "text/plain";
        }
        boolean isBinary = mimeType.startsWith("image") || mimeType.startsWith("video") ||
                mimeType.startsWith("audio");
        if (isBinary) {
            var bytes = IoUtil.readInBinaryFile(f);
            return new DnContentData(mimeType, true, null, bytes, resTimestamp);
        } else {
            String resp = IoUtil.readInFile(f);
            return new DnContentData(mimeType, false, resp, null, resTimestamp);
        }
    }

    public DnContentData applyHtmlLayout(Map<String,Object> params) throws DnException {
        var layoutFile = getFileResource("layout/layout.ftl");
        DnTemplates.DnTemplate layoutTemplate = templates.checkGetTemplate("layout/layout.ftl",
                layoutFile, null);

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
