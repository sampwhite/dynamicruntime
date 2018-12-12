package org.dynamicruntime.content;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.startup.ServiceInitializer;

import org.dynamicruntime.util.IoUtil;
import org.dynamicruntime.util.StrUtil;

import java.io.File;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("WeakerAccess")
public class DnContentService implements ServiceInitializer {
    public static final String DN_CONTENT_SERVICE = DnContentService.class.getSimpleName();
    ClassLoader classLoader;
    public FileNameMap fileNameMap;
    public Parser mdParser;
    public HtmlRenderer mdHtmlRenderer;


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
    }
        @Override
    public void checkInit(DnCxt cxt) {

    }

    public DnContentData getContent(DnCxt cxt, String resourcePath) throws DnException {
        var resourceUrl = classLoader.getResource(resourcePath);
        if (resourceUrl == null) {
            throw DnException.mkFileIo(String.format("Cannot find resource %s.", resourcePath), null,
                    DnException.NOT_FOUND);
        }
        File f = new File(resourceUrl.getFile());
        String ext = StrUtil.getAfterLastIndex(resourcePath, ".");
        byte[] bytes = IoUtil.readInBinaryFile(f);
        if ("md".equals(ext)) {
            Node node = mdParser.parse(convertToStr(bytes));
            Node child = node.getFirstChild();
            Node childNext = child != null ? child.getFirstChild() : null;
            while (childNext != null && !(child instanceof Text)) {
                child = childNext;
                childNext = childNext.getNext();
            }
            String title = (child instanceof Text) ? ((Text)child).getLiteral() : "Dynamic Runtime";
            String layout = getStrResource("md/layout.html");
            String mdText = convertMarkdown(cxt, convertToStr(bytes));
            String str1 = layout.replace("{{title}}", title);
            String str2 = str1.replace("{{body}}", mdText);

            return DnContentData.mkHtml(str2);
        }

        String mimeType = fileNameMap.getContentTypeFor(resourcePath);
        if (mimeType == null && resourcePath.endsWith(".ico")) {
            mimeType = "image/ico";
        }
        if (mimeType == null) {
            mimeType = "text/plain";
        }
        boolean isBinary = mimeType.startsWith("image") || mimeType.startsWith("video") ||
                mimeType.startsWith("audio");
        if (isBinary) {
            return new DnContentData(mimeType, true, null, bytes);
        } else {
            return new DnContentData(mimeType, false, convertToStr(bytes), null);
        }
    }

    public String convertMarkdown(@SuppressWarnings("unused") DnCxt cxt, String markdown) {
        Node node = mdParser.parse(markdown);
        return mdHtmlRenderer.render(node);
    }

    public static String convertToStr(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public String getStrResource(String resourcePath) throws DnException {
        var resourceUrl = classLoader.getResource(resourcePath);
        if (resourceUrl == null) {
            throw DnException.mkFileIo(String.format("Cannot find text resource %s.", resourcePath), null,
                    DnException.NOT_FOUND);
        }
        File f = new File(resourceUrl.getFile());
        return convertToStr(IoUtil.readInBinaryFile(f));
    }
}
