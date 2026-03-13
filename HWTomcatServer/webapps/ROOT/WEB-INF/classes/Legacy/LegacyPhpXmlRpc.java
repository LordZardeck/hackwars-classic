package Legacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class LegacyPhpXmlRpc {
    private static final Hashtable BOOKMARKS_BY_IP = new Hashtable();
    private static final Hashtable SETTINGS_BY_IP = new Hashtable();
    private static final Hashtable PROFILE_BY_USER = new Hashtable();
    private static final AtomicInteger NEXT_BOOKMARK_ID = new AtomicInteger(1000);

    public Object[] login(String clientDate) {
        return new Object[]{Boolean.TRUE, "Local date check passed."};
    }

    public Object[] login(String username, String password, String clientDate) {
        boolean valid = username != null && username.trim().length() > 0;
        String message = valid ? "Login successful." : "<html><font color=\"#FF0000\">Please enter a username.</font></html>";
        return new Object[]{Boolean.valueOf(valid), defaultPlayerIP(), Boolean.FALSE, "", message};
    }

    public String domainLookup(String input) {
        return normalizeDomain(input);
    }

    public String domainLookup(String input, HashMap query) {
        return normalizeDomain(input);
    }

    public String reverseLookup(String input) {
        return normalizeDomain(input);
    }

    public synchronized Object[] getBookmarks(String ip) {
        ArrayList list = getBookmarksList(ip);
        return (Object[]) list.toArray(new Object[list.size()]);
    }

    public synchronized String addBookmark(String ip, String domain, String name, String folder) {
        if (name == null || name.trim().length() == 0) {
            name = domain;
        }
        if (folder == null) {
            folder = "";
        }
        String id = String.valueOf(NEXT_BOOKMARK_ID.getAndIncrement());
        getBookmarksList(ip).add(new Object[]{domain, name, folder, id});
        return id;
    }

    public synchronized boolean editBookmark(int id, String name) {
        return editBookmarkById(String.valueOf(id), name);
    }

    public synchronized boolean editBookmark(String id, String name) {
        return editBookmarkById(id, name);
    }

    public synchronized boolean deleteBookmark(int id) {
        return deleteBookmarkById(String.valueOf(id));
    }

    public synchronized boolean deleteBookmark(String id) {
        return deleteBookmarkById(id);
    }

    public Object[] getFunctionPacks(String ip) {
        boolean proPack = Boolean.parseBoolean(getPropertySafe("hackwars.proPack", "false"));
        boolean inactive = Boolean.parseBoolean(getPropertySafe("hackwars.inactive", "false"));
        return new Object[]{Boolean.FALSE, Boolean.FALSE, Boolean.valueOf(proPack), Boolean.valueOf(inactive)};
    }

    public synchronized Object[] getSettings(String ip) {
        Object[] settings = (Object[]) SETTINGS_BY_IP.get(ip);
        if (settings == null) {
            settings = new Object[]{Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, defaultPortColumns()};
            SETTINGS_BY_IP.put(ip, settings);
        }
        return settings;
    }

    public synchronized boolean setPortColumns(String ip, Object[] columns) {
        Object[] settings = getSettings(ip);
        settings[4] = normalizePortColumns(columns);
        SETTINGS_BY_IP.put(ip, settings);
        return true;
    }

    public Object[] getTutorial(String key) {
        String msg = "<p>Welcome to local HackWars mode.</p><p>Remote tutorial content is unavailable, but gameplay is enabled.</p>";
        return new Object[]{msg, "Tutorial"};
    }

    public String getFile(Integer soundIndex) {
        return "";
    }

    public synchronized HashMap getProfile(String username) {
        HashMap profile = (HashMap) PROFILE_BY_USER.get(username);
        if (profile == null) {
            profile = new HashMap();
            profile.put("image", "images/nopic.png");
            profile.put("width", new Integer(160));
            profile.put("height", new Integer(160));
            profile.put("description", "");
            profile.put("location", "");
            profile.put("ip", defaultPlayerIP());
            PROFILE_BY_USER.put(username, profile);
        }
        return profile;
    }

    public synchronized boolean saveImage(String username, String image) {
        HashMap profile = getProfile(username);
        profile.put("image", image);
        return true;
    }

    public synchronized boolean saveDescription(String username, String description) {
        HashMap profile = getProfile(username);
        profile.put("description", description == null ? "" : description);
        return true;
    }

    public synchronized boolean saveLocation(String username, String location) {
        HashMap profile = getProfile(username);
        profile.put("location", location == null ? "" : location);
        return true;
    }

    public String sendEmail(String ip, String message) {
        return "ok";
    }

    public String sendFacebook(String ip, String targetIP, String message) {
        return "ok";
    }

    public String updateFacebook(String ip, Double pettyCash, Double bankMoney, Integer defaultBank) {
        return "ok";
    }

    public String deleteLogs(String ip, String sourceIP) {
        return "ok";
    }

    private String normalizeDomain(String input) {
        if (input == null) {
            return "";
        }
        String value = input.trim();
        if (value.length() == 0) {
            return "";
        }
        try {
            if (value.indexOf("://") != -1) {
                java.net.URL u = new java.net.URL(value);
                if (u.getHost() != null && u.getHost().length() > 0) {
                    return u.getHost().toLowerCase();
                }
            }
        } catch (Exception e) {
        }
        if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        } else if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.toLowerCase();
    }

    private synchronized ArrayList getBookmarksList(String ip) {
        if (ip == null || ip.trim().length() == 0) {
            ip = defaultPlayerIP();
        }
        ArrayList list = (ArrayList) BOOKMARKS_BY_IP.get(ip);
        if (list == null) {
            list = new ArrayList();
            BOOKMARKS_BY_IP.put(ip, list);
        }
        return list;
    }

    private boolean editBookmarkById(String id, String name) {
        if (id == null) {
            return false;
        }
        for (Iterator it = BOOKMARKS_BY_IP.values().iterator(); it.hasNext(); ) {
            ArrayList list = (ArrayList) it.next();
            for (int i = 0; i < list.size(); i++) {
                Object[] bookmark = (Object[]) list.get(i);
                if (bookmark != null && bookmark.length > 3 && id.equals(String.valueOf(bookmark[3]))) {
                    bookmark[1] = name;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean deleteBookmarkById(String id) {
        if (id == null) {
            return false;
        }
        for (Iterator it = BOOKMARKS_BY_IP.values().iterator(); it.hasNext(); ) {
            ArrayList list = (ArrayList) it.next();
            for (Iterator bit = list.iterator(); bit.hasNext(); ) {
                Object[] bookmark = (Object[]) bit.next();
                if (bookmark != null && bookmark.length > 3 && id.equals(String.valueOf(bookmark[3]))) {
                    bit.remove();
                    return true;
                }
            }
        }
        return false;
    }

    private Object[] defaultPortColumns() {
        return new Object[]{
                Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE,
                Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE
        };
    }

    private Object[] normalizePortColumns(Object[] columns) {
        Object[] defaults = defaultPortColumns();
        if (columns == null) {
            return defaults;
        }
        int max = Math.min(columns.length, defaults.length);
        for (int i = 0; i < max; i++) {
            Object o = columns[i];
            if (o instanceof Boolean) {
                defaults[i] = o;
            } else if (o != null) {
                defaults[i] = Boolean.valueOf(String.valueOf(o));
            }
        }
        return defaults;
    }

    private String defaultPlayerIP() {
        return getPropertySafe("hackwars.player.ip", "192.168.2.002");
    }

    private String getPropertySafe(String key, String fallback) {
        try {
            return System.getProperty(key, fallback);
        } catch (SecurityException e) {
            return fallback;
        }
    }
}
