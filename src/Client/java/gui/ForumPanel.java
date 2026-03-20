package gui;

import browser.HtmlHandler;
import util.LegacyRemoteDefaults;

import javax.swing.JInternalFrame;
import java.awt.Rectangle;
import java.net.URL;

public class ForumPanel extends HtmlHandler {

    public static final int CATEGORY = 0;
    public static final int THREADS = 1;
    public static final int THREAD = 2;
    public static final int PROFILE = 3;

    private final Rectangle size;
    private final WebBrowser MyWebBrowser;
    private final Hacker MyHacker;
    private final String url;
    private final int[] type = new int[10];
    private int index = 0;
    private final String[] id = new String[10];
    private final URL[] page = new URL[10];

    public ForumPanel(String url, Rectangle size, WebBrowser MyWebBrowser, Hacker MyHacker) {
        this.size = size;
        this.url = url;
        this.MyWebBrowser = MyWebBrowser;
        this.MyHacker = MyHacker;
        setParent(this);
        renderUnavailable();
    }

    public void showCategories() {
        renderUnavailable();
    }

    public void showThreads() {
        renderUnavailable();
    }

    public void showThread() {
        renderUnavailable();
    }

    public void showProfile() {
        unavailableMessage();
    }

    public void goBack() {
        if (index != 0) {
            index--;
            renderUnavailable();
        }
    }

    public void goForward() {
        if (index != 10 && id[index + 1] != null) {
            index++;
            renderUnavailable();
        }
    }

    public void resize() {
        renderUnavailable();
    }

    public void increaseIndex() {
        index++;
    }

    public void setType(int type) {
        this.type[index] = type;
    }

    public void setID(String id) {
        this.id[index] = id;
    }

    public void edit(String id) {
        // TODO: Removed legacy forum edit endpoints: http://www.hackwars.net/forum/edit.php and http://www.hackwars.net/forum/editmessage.php
        unavailableMessage();
    }

    public void editMessage(String message, String id) {
        // TODO: Removed legacy forum edit endpoints: http://www.hackwars.net/forum/edit.php and http://www.hackwars.net/forum/editmessage.php
        unavailableMessage();
    }

    public void reply(String message) {
        // TODO: Removed legacy forum reply endpoint: http://www.hackwars.net/forum/reply.php
        unavailableMessage();
    }

    public void createNewThread(String message, String subject) {
        // TODO: Removed legacy forum thread creation endpoint: http://www.hackwars.net/forum/newthread.php
        unavailableMessage();
    }

    public void shiftIndex() {
        for (int i = 1; i < 10; i++) {
            page[i - 1] = page[i];
        }
        index = 9;
    }

    public void setLink(URL link) {
        if (index == 10) {
            shiftIndex();
        }
        page[index] = link;
        // TODO: Removed legacy forum browsing endpoints: http://www.hackwars.net/forum/categories.php, http://www.hackwars.net/forum/threads.php and http://www.hackwars.net/forum/viewthread.php
        renderUnavailable();
    }

    private void renderUnavailable() {
        // TODO: Removed legacy forum browsing endpoints: http://www.hackwars.net/forum/categories.php, http://www.hackwars.net/forum/threads.php and http://www.hackwars.net/forum/viewthread.php
        parseDocument(
            LegacyRemoteDefaults.unavailableHtml(
                "Forum unavailable",
                "Legacy forum content is unavailable in this build."
            ),
            (JInternalFrame) MyWebBrowser
        );
    }

    private void unavailableMessage() {
        MyHacker.showMessage("Forum is unavailable in this build.");
    }
}
