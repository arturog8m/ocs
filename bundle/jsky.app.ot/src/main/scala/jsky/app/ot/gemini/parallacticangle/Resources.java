package jsky.app.ot.gemini.parallacticangle;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Resources provides a central class with methods for accessing
 * project resources.
 */
public final class Resources {
    /**
     * Path to resources.
     */
    public static final String RESOURCE_PATH = "/resources";

    /**
     * Subpath to images, within the resources area.
     */
    public static final String IMAGES_SUBPATH = "images";

    /**
     * Subpath to config files, within the resources area.
     */
    public static final String CONFIG_SUBPATH = "conf";

    // ImageCache provides a cache of already present icons.
    private static Map<String, Icon> _rmap = new HashMap<>();

    // Disallow instances
    private Resources() {
    }

    /**
     * Gets a URL associated with the given resource.
     *
     * @return a URL pointing to the file, null otherwise
     */
    public static URL getResource(String fileName) {
        String path = RESOURCE_PATH + '/' + fileName;
        URL u = Resources.class.getResource(path);
        if (u == null) {
            System.out.println("Failed to get: " + path);
        }
        return u;
    }


    /**
     * Returns an Icon from the specified filename.
     *
     * @param iconFileName The relative path name to the image file.
     *                     For example, "flag.gif".  It is assumed the image file is
     *                     being properly installed to the resources directory.
     * @return Icon constructed from data in <code>iconFileName</code>.
     *         Even though the method interface can't guarentee this, the icon
     *         implementation will be <code>Serializable</code>.
     *         Returns null (does not throw an Exception) if specified
     *         resource is not found.
     */
    public static Icon getIcon(String iconFileName) {
        // First check the map under the iconFileName
        Icon icon = _rmap.get(iconFileName);
        if (icon != null) {
            //System.out.println("Found icon: " + iconFileName);
            return icon;
        }

        // Turn the file name into a resource path
        URL url = getResource(IMAGES_SUBPATH + "/" + iconFileName);
        if (url == null) {
            return null;
        }

        // If constructor fails, don't want to store null reference
        icon = new ImageIcon(url);
        _rmap.put(iconFileName, icon);
        return icon;
    }


    /**
     * Loads an installed <code>Properties</code> file.
     *
     * @param fileName relative path to the configuration file (which must be
     *                 loadable by the <code>java.util.Properties</code> class)
     * @return a Properties object created from the configuration file; null if
     *         the file does not exist
     */
    public static Properties getProperties(String fileName)
            throws IOException {
        URL url = getResource(CONFIG_SUBPATH + "/" + fileName);
        if (url == null) {
            return null;
        }

        Properties props = null;
        BufferedInputStream bis = null;

        try {
            bis = new BufferedInputStream(url.openStream());
            props = new Properties();
            props.load(bis);
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }

        return props;
    }
}
