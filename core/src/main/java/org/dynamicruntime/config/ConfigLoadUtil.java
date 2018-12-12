package org.dynamicruntime.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.ConvertUtil;
import org.dynamicruntime.util.IoUtil;
import org.dynamicruntime.util.LogUtil;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class ConfigLoadUtil {
    private static String workingDir;

    public static String getWorkingDir() {
        if (workingDir == null) {
            workingDir = System.getProperty("user.dir");
        }
        return workingDir;
    }

    /** Only for unusual launching should this be set explicitly. */
    @SuppressWarnings("unused")
    public static void setWorkingDir(String wDir) {
        ConfigLoadUtil.workingDir = wDir;
    }

    public static Map<String,Object> parseYamlText(String text) throws IOException  {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return ConvertUtil.toOptMap(mapper.readValue(text, Map.class));
    }

    public static Map<String,Object> parseYaml(File file) throws DnException {
        try {
            return parseYamlText(IoUtil.readInFile(file));
        } catch (JsonProcessingException e) {
            throw DnException.mkConv(String.format("Could not parse YAML in file %s.", file.getAbsolutePath()),
                e);
        } catch (IOException e) {
            throw DnException.mkFileIo(String.format("Could not read in YAML file %s.", file.getAbsolutePath()),
                e, (e instanceof FileNotFoundException) ? DnException.NOT_FOUND : DnException.INTERNAL_ERROR);
        }
    }

    public static Map<String,Object> parseYamlResource(String resPath) throws DnException {
        String text = IoUtil.readInResource(resPath);
        if (text == null) {
            throw DnException.mkFileIo(String.format("Could not read in resource at path %s.", resPath),
                    null, DnException.NOT_FOUND);
        }
        try {
            return parseYamlText(text);
        } catch (IOException e) {
            throw DnException.mkConv(String.format("Could not parse YAML text at resource path %s.", resPath),
                    e);
        }
    }

    /** Hunts up to three parent directories for a file. */
    public static File findConfigFile(String filePathSuffix) {
        // Start at the working directory and go up the parents.
        File curParent =  new File(getWorkingDir());
        File retVal = null;
        for (int i = 0; i < 4; i++) {
            File testFile = new File(curParent, filePathSuffix);
            if (testFile.exists()) {
               retVal = testFile;
               break;
            }
            curParent = curParent.getParentFile();
            if (curParent == null) {
                break;
            }
            // Fix window paths.
            String testPath = curParent.getAbsolutePath();
            int index = testPath.indexOf('/');
            if ((index < 0 || index > testPath.length() - 5)) {
                // Do not go up to root or root plus a short directory name.
                break;
            }
        }
        return retVal;
    }

    /** Hunts up to three parents of the current working directory to find a particular relative file path.
     * Useful for doing startup from working directories in other source code directories not managed by
     * this repository. */
    public static Map<String,Object> findAndReadYamlFile(DnCxt cxt, String filePathSuffix) throws DnException {
        File configFile = findConfigFile(filePathSuffix);
        Map<String,Object> retVal = null;
        if (configFile != null) {
            retVal =  parseYaml(configFile);
            LogUtil.log.debug(cxt, String.format("Found YAML file %s at location %s.", filePathSuffix,
                    configFile.getAbsolutePath()));
        }
        if (retVal == null) {
            LogUtil.log.info(cxt, String.format("Could not find file %s hunting up " +
                            "(up to three) parent directories from working dir",
                    filePathSuffix));
        }
        return retVal;
    }

    public static Map<String,Object> resolveConfig(DnCxt cxt, Map<String,Object> config) {
        var newConfig = cloneMap(config);

        // Overlay in order. Instance config trumps environments which trumps envTypes.
        overlayMap(newConfig, ConfigConstants.INSTANCES, cxt.instanceConfig.instanceName);
        overlayMap(newConfig, ConfigConstants.ENVIRONMENTS, cxt.instanceConfig.envName);
        overlayMap(newConfig, ConfigConstants.ENV_TYPES, cxt.instanceConfig.envType);
        // Let defaults provide values if not already present.
        newConfig = underlayDefaults(newConfig, 0);
        // Turn {key1: {key2 : 1}} into {key1.key2 : 1} constructs.
        newConfig = collapseMaps(newConfig);

        return newConfig;
    }

    public static void overlayMap(Map<String,Object> data, String key1, String key2) {
        if (key2 == null) {
            return;
        }
        Map<String,Object> overlayData = removeMap(data, key1);
        Map<String,Object> ourOverlayData = getOptMap(overlayData, key2);
        if (ourOverlayData != null) {
            mergeMapRecursively(data, ourOverlayData);
        }
    }

    public static Map<String,Object> removeMap(Map<String,Object> data, String key) {
        Object obj = data.remove(key);
        return ConvertUtil.toOptMap(obj);
    }

    public static Map<String,Object> underlayDefaults(Map<String,Object> data, int nestLevel) {
        if (nestLevel > 2) {
            return data;
        }
        Map<String,Object> dflts = removeMap(data, ConfigConstants.DEFAULT);
        if (dflts == null) {
            return data;
        }
        dflts = underlayDefaults(dflts, nestLevel + 1);
        mergeMapRecursively(dflts, data);
        return dflts;
    }
}
