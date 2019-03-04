package org.dynamicruntime.content;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.SimpleNumber;
import freemarker.template.SimpleScalar;
import freemarker.template.Template;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnFunction;
import org.dynamicruntime.util.IoUtil;

import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnTemplates {
    public static class DnTemplate {
        public final String path;
        public final String content;
        public final Template template;
        public final Map<String,Object> baseParams;
        public final Date lastModified;

        public DnTemplate(String path, String content, Template template,
                Map<String,Object> baseParams, Date lastModified) {
            this.path = path;
            this.content = content;
            this.template = template;
            this.baseParams = baseParams;
            this.lastModified = lastModified;
        }
    }
    public static class DnOutput {
        public String output;
        public Map<String,Object> outVals;

        public DnOutput(String output, Map<String,Object> outVals) {
            this.output = output;
            this.outVals = outVals;
        }

    }
    public final Configuration fmConfig = new Configuration(Configuration.VERSION_2_3_28);
    public final StringTemplateLoader fmLoader = new StringTemplateLoader();
    public final Map<String,DnTemplate> templates = mMapT();


    public DnTemplates() {
        fmConfig.setTemplateLoader(fmLoader);
    }

    public DnOutput evalTemplate(DnTemplate dnTemplate, Map<String,Object> params) throws DnException {
        StringWriter sw = new StringWriter();
        try {
            var exec = dnTemplate.template.createProcessingEnvironment(params, sw);
            exec.process();
            var namespace = exec.getCurrentNamespace();
            var keys = namespace.keys().iterator();
            Map<String,Object> outVals = mMap();
            // Though keys calls itself an iterator, it does not actually implement the standard
            // Java Iterator interface.
            while (keys.hasNext()) {
                var k = keys.next().toString();
                Object o = namespace.get(k);
                Object val = null;
                if (o instanceof SimpleScalar) {
                    val = o.toString();
                } else if (o instanceof SimpleNumber) {
                    val = ((SimpleNumber)o).getAsNumber();
                }
                if (val != null) {
                     outVals.put(k, val);
                }
            }
            return new DnOutput(sw.toString(), outVals);
        } catch (Exception e) {
            throw new DnException(String.format("Cannot evaluate template %s.", dnTemplate.path), e);
        }
    }

    public DnTemplate checkGetTemplate(String path, File file, DnFunction<String, DnOutput> mkOutput) throws DnException {
        synchronized (templates) {
            DnTemplate t = templates.get(path);
            long ts = file.lastModified();
            if (t == null || t.lastModified.getTime() != ts) {
                String content = IoUtil.readInFile(file);
                DnOutput contentOutput = (mkOutput != null) ? mkOutput.apply(content) :
                        new DnOutput(content, mMap());
                String key = (path.startsWith("/")) ? path.substring(1) : path;
                if (contentOutput.output != null) {
                     fmLoader.putTemplate(key, contentOutput.output, ts);
                }
                try {
                    var fmTemplate = (contentOutput.output != null) ? fmConfig.getTemplate(key) : null;
                    t = new DnTemplate(path, contentOutput.output, fmTemplate, contentOutput.outVals, new Date(ts));
                    templates.put(path, t);
                } catch (IOException e) {
                    throw DnException.mkFileIo(
                            String.format("Could not get FreeMarker template for %s.", file.toString()), e,
                                DnException.INTERNAL_ERROR);
                }
            }
            return t;
        }
    }
}
