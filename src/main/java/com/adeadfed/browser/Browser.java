package com.adeadfed.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import com.adeadfed.common.OsType;
import com.adeadfed.common.ProfileColors;
import com.adeadfed.browser_extensions.BrowserExtensionsLoader;

public class Browser {
    private String exePath;
    private String profilesPath;
    private String profileColor;
    private String profileName;

    private final String BROWSER_DATA_PREFIX = "browser-data";

    // Burp keeps its browser data folder and its built-in browser extensions here
    public static final Path BURP_USER_DIR = OsType.isWindows()
            ? Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "BurpSuite")
            : Paths.get(System.getProperty("user.home"), ".BurpSuite");

    // each entry belongs to one extension; only the Burp browser's entries are replaced,
    // so PwnFox's own extensions keep their settings
    private final String[] BURP_BROWSER_EXTENSION_DIRS = {
        "Default/Extensions",
        "Default/Local Extension Settings",
        "Default/Sync Extension Settings"
    };

    // the extensions Burp loads into its own browser
    private final String[] BURP_BUILT_IN_EXTENSIONS = {
        "dom-invader",
        "navigation-recorder",
        "new-tab"
    };

    // only what is listed is copied, so cookies, logins, history and autofill stay out
    private final String[] BURP_BROWSER_FILES = {
        "Local State",
        "Default/Preferences",
        "Default/Secure Preferences",
        "Default/Bookmarks"
    };

    // taken from args passed to the built-in BurpSuite Chromium browser
    private final String[] PWNCHROME_DEFAULT_ARGS = {
        "--ignore-certificate-errors",
        "--disable-ipc-flooding-protection",
        "--disable-xss-auditor",
        "--disable-bundled-ppapi-flash",
        "--disable-plugins-discovery",
        "--disable-default-apps",
        "--disable-prerender-local-predictor",
        "--disable-sync",
        "--disable-breakpad",
        "--disable-crash-reporter",
        "--disable-prerender-local-predictor",
        "--disk-cache-size=0",
        "--disable-settings-window",
        "--disable-notifications",
        "--disable-speech-api",
        "--disable-file-system",
        "--disable-presentation-api",
        "--disable-permissions-api",
        "--disable-new-zip-unpacker",
        "--disable-media-session-api",
        "--no-experiments",
        "--no-events",
        "--no-first-run",
        "--no-default-browser-check",
        "--no-pings",
        "--no-service-autorun",
        "--media-cache-size=0",
        "--use-fake-device-for-media-stream",
        "--dbus-stub",
        "--disable-background-networking",
        "--disable-features=ChromeWhatsNewUI,HttpsUpgrades,ImageServiceObserveSyncDownloadStatus"
    };

    public Browser(String exePath, String profilesPath, String themeColor, String profileName) {
        this.exePath = exePath;
        this.profilesPath = profilesPath;
        this.profileColor = themeColor;
        this.profileName = profileName;
    }

    public Process start() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(getArgs());
        Process process = processBuilder.start();
        // ensure browser is closed when burp is exited
        Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
        return process;
    }

    public void copyBurpBrowserData(String burpBrowserDataPath) throws IOException {
        Path userDataDir = Paths.get(getUserDataDir());
        // an open profile is using these files; it gets them at its next launch
        if (Files.exists(userDataDir.resolve("SingletonLock"), LinkOption.NOFOLLOW_LINKS) ||
                Files.exists(userDataDir.resolve("lockfile"))) {
            return;
        }

        Path burpBrowserDataDir = Paths.get(burpBrowserDataPath);
        if (!Files.isDirectory(burpBrowserDataDir.resolve("Default"))) {
            throw new NoSuchFileException(burpBrowserDataDir.resolve("Default").toString());
        }

        // folders first, so a failed copy never registers extensions whose files are missing
        for (String dir : BURP_BROWSER_EXTENSION_DIRS) {
            Path source = burpBrowserDataDir.resolve(dir);
            if (!Files.isDirectory(source)) {
                continue;
            }
            try (Stream<Path> entries = Files.list(source)) {
                for (Path entry : entries.toList()) {
                    replace(entry, userDataDir.resolve(dir).resolve(entry.getFileName().toString()));
                }
            }
        }
        for (String file : BURP_BROWSER_FILES) {
            replace(burpBrowserDataDir.resolve(file), userDataDir.resolve(file));
        }
    }

    private String[] getArgs() throws Exception {
        return concatAll(
                getExeArgs(),
                PWNCHROME_DEFAULT_ARGS,
                getProfileArgs(),
                getExtensionsArgs()
        );
    }

    private String[] getExeArgs() {
        return new String[] { exePath };
    } 

    private String[] getExtensionsArgs() throws IOException {
        BrowserExtensionsLoader browserExtensions = new BrowserExtensionsLoader(
                profilesPath,
                ProfileColors.valueOf(profileColor.toUpperCase())
        );
        // PwnFox's extensions are installed last, so they keep control of the proxy and the theme
        String[] extensionDirs = concatAll(getBurpBuiltInExtensionDirs(), new String[] {
                browserExtensions.getHeaderExtensionDir(),
                browserExtensions.getProxyExtensionDir(),
                browserExtensions.getThemeDir()
        });
        return new String[] { "--load-extension=" + String.join(",", extensionDirs) };
    }

    private String[] getBurpBuiltInExtensionDirs() {
        return Arrays.stream(BURP_BUILT_IN_EXTENSIONS)
                .map(BURP_USER_DIR::resolve)
                .filter(Files::isDirectory)
                .map(Path::toString)
                .toArray(String[]::new);
    }

    private String getUserDataDir() throws InvalidPathException {
        return Paths.get(
                profilesPath,
                BROWSER_DATA_PREFIX,
                profileColor.toLowerCase()
        ).toString();
    }

    private String[] getProfileArgs() throws InvalidPathException {
        String userDataDirArg = "--user-data-dir=" + getUserDataDir();
        String windowNameArg = "--window-name=" + "PwnChromium - " + profileName;
        return new String[] { userDataDirArg, windowNameArg };
    }

    private static void replace(Path source, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> paths = Files.walk(target)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        if (!Files.exists(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Files.copy(path, target.resolve(source.relativize(path).toString()));
            }
        }
    }

    // https://stackoverflow.com/questions/80476/how-can-i-concatenate-two-arrays-in-java
    @SafeVarargs
    private static <T> T[] concatAll(T[] first, T[]... rest) {
        int totalLength = first.length;
        for (T[] array : rest) {
            totalLength += array.length;
        }
        T[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (T[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}