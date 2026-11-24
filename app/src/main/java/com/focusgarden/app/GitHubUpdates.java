package com.focusgarden.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

final class GitHubUpdates {
    static final String MIME = "application/vnd.android.package-archive";
    static final String DEFAULT_REPOSITORY = "Jansongzi/FocusGarden";
    private static final String GITEE_REPOSITORY = "jiansz/focusgarden";
    static final String GITEE_RELEASES_URL = "https://gitee.com/" + GITEE_REPOSITORY + "/releases";
    private static final long MAX_APK = 150L * 1024 * 1024;
    private static final Set<String> GITHUB_DOWNLOAD_HOSTS = new HashSet<>(Arrays.asList("github.com",
            "objects.githubusercontent.com", "release-assets.githubusercontent.com", "github-releases.githubusercontent.com"));
    private static final Set<String> GITEE_DOWNLOAD_HOSTS = new HashSet<>(Arrays.asList("gitee.com", "foruda.gitee.com"));
    private static final Pattern GITEE_CHECKSUM = Pattern.compile("(?m)^SHA-256: ([a-fA-F0-9]{64})[ \\t]*\\r?$");
    private static final Pattern GITEE_WEB_RELEASE = Pattern.compile(
            "<div class=['\"]release-tag-item['\"] data-tag=['\"]([^'\"]+)['\"][^>]*>");
    private static final Pattern GITEE_WEB_LATEST = Pattern.compile("class=['\"]ui label latest-version['\"]");
    private static final Pattern GITEE_WEB_NOTES = Pattern.compile(
            "<textarea class=['\"]content['\"][^>]*>(.*?)</textarea>", Pattern.DOTALL);
    private static volatile PackageInfo cachedInstalled;

    record Asset(String name, URL url, long size, String sha) {}
    record Release(String version, String notes, List<Asset> assets) {}

    static PackageInfo installed(Context context) {
        if (cachedInstalled != null) return cachedInstalled;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), flags());
            cachedInstalled = info;
            return info;
        } catch (PackageManager.NameNotFoundException impossible) {
            throw new IllegalStateException("无法读取当前版本信息", impossible);
        }
    }

    static File pending(Context context) {
        return new File(context.getCacheDir(), "focus-garden-update.apk");
    }

    static Uri installUri(Context context) {
        return Uri.parse("content://" + context.getPackageName() + ".updates/update.apk");
    }

    static String repository(Context context) {
        return DEFAULT_REPOSITORY;
    }

    static boolean hasPending(Context context, PackageInfo current) {
        long downloaded = context.getSharedPreferences("github_updates", Context.MODE_PRIVATE).getLong("downloadedCode", 0);
        return downloaded > code(current) && pending(context).isFile();
    }

    static Release latestUpdate(String installedVersion) throws Exception {
        return trySources(() -> {
            Release release = giteeRelease("latest");
            if (UpdateRules.compare(release.version, installedVersion) < 0) {
                throw new IOException("国内更新源版本落后");
            }
            return release;
        }, () -> latest(DEFAULT_REPOSITORY));
    }

    static <T> T trySources(Callable<T> domestic, Callable<T> github) throws Exception {
        try {
            return domestic.call();
        } catch (Exception domesticError) {
            try {
                return github.call();
            } catch (Exception githubError) {
                githubError.addSuppressed(domesticError);
                throw githubError;
            }
        }
    }

    private static Release giteeRelease(String releasePath) throws Exception {
        Set<String> host = Collections.singleton("gitee.com");
        try {
            String body = readText(new URL("https://gitee.com/api/v5/repos/" + GITEE_REPOSITORY
                    + "/releases/" + releasePath), host, false, 1_000_000);
            return parseGiteeRelease(new JSONObject(body));
        } catch (Exception apiError) {
            try {
                String html = readText(new URL(GITEE_RELEASES_URL), host, false, 1_000_000);
                String wantedVersion = releasePath.equals("latest") ? null : releasePath.substring("tags/".length());
                return parseGiteeReleasesPage(html, wantedVersion);
            } catch (Exception pageError) {
                pageError.addSuppressed(apiError);
                throw pageError;
            }
        }
    }

    static Release parseGiteeReleasesPage(String html, String wantedVersion) throws IOException {
        Matcher entries = GITEE_WEB_RELEASE.matcher(html);
        boolean hasEntry = entries.find();
        while (hasEntry) {
            String version = entries.group(1);
            int start = entries.end();
            hasEntry = entries.find();
            String block = html.substring(start, hasEntry ? entries.start() : html.length());
            if (wantedVersion == null ? !GITEE_WEB_LATEST.matcher(block).find() : !wantedVersion.equals(version)) {
                continue;
            }
            UpdateRules.compare(version, version);
            String name = "FocusGarden-" + version + ".apk";
            URL url = giteeDownloadUrl(version, name);
            String attachment = "href=['\"]" + Pattern.quote(url.getPath()) + "['\"]";
            Matcher notes = GITEE_WEB_NOTES.matcher(block);
            if (!Pattern.compile(attachment).matcher(block).find() || !notes.find()) {
                throw new IOException("国内发行页暂无可用安装包");
            }
            String decoded = unescapeEntities(notes.group(1).replace("&#x000A;", "\n").replace("&#10;", "\n"));
            return giteeReleaseWithAsset(version, name, url, 0L, decoded);
        }
        throw new IOException("国内发行页暂无对应正式版本");
    }

    static Release parseGiteeRelease(JSONObject json) throws Exception {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) throw new IOException("国内更新源暂无正式版本");
        String version = json.getString("tag_name");
        UpdateRules.compare(version, version);
        String name = "FocusGarden-" + version + ".apk";
        URL expected = giteeDownloadUrl(version, name);
        String notes = json.isNull("body") ? "" : json.optString("body", "");
        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject value = assets.getJSONObject(i);
            if (!name.equals(value.optString("name"))) continue;
            if (!expected.toExternalForm().equals(value.optString("browser_download_url"))) {
                throw new IOException("国内安装包下载地址校验不通过");
            }
            long size = value.optLong("size");
            if (size < 0 || size > MAX_APK) throw new IOException("国内安装包大小无效");
            return giteeReleaseWithAsset(version, name, expected, size, notes);
        }
        throw new IOException("国内更新源暂无可用安装包");
    }

    private static Release giteeReleaseWithAsset(String version, String name, URL url, long size, String notes)
            throws IOException {
        Matcher checksum = GITEE_CHECKSUM.matcher(notes);
        if (!checksum.find()) throw new IOException("国内更新源缺少安装包校验信息");
        String sha = UpdateRules.sha256("sha256:" + checksum.group(1));
        notes = checksum.replaceAll("").trim();
        if (notes.length() > 1400) notes = notes.substring(0, 1400) + "…";
        return new Release(version, notes, Collections.singletonList(new Asset(name, url, size, sha)));
    }

    private static URL giteeDownloadUrl(String version, String name) throws IOException {
        return new URL(GITEE_RELEASES_URL + "/download/" + version + "/" + name);
    }

    static Release latest(String repository) throws Exception {
        try {
            return latestFromWeb(repository);
        } catch (IOException webError) {
            String msg = webError.getMessage();
            if ("暂无正式版本".equals(msg) || "暂无可用的正式版本".equals(msg)) throw webError;
            try {
                return latestFromApi(repository);
            } catch (Exception ignored) {
                throw webError;
            }
        }
    }

    static Release latestFromWeb(String repository) throws Exception {
        URL latestUrl = new URL("https://github.com/" + repository + "/releases/latest");
        HttpsURLConnection c = openSingle(latestUrl, Collections.singleton("github.com"), false);
        String redirectLocation;
        try {
            int status = c.getResponseCode();
            if (status == 200) throw new IOException("暂无正式版本");
            if (status == 404) throw new IOException("暂无可用的正式版本");
            if (status < 300 || status >= 400) checkStatus(status);
            redirectLocation = c.getHeaderField("Location");
        } finally {
            c.disconnect();
        }
        if (redirectLocation == null || redirectLocation.isEmpty()) {
            throw new IOException("更新信息解析失败，请稍后重试");
        }
        URL tagUrl = new URL(latestUrl, redirectLocation);
        if (!validHost(tagUrl, Collections.singleton("github.com"))) {
            throw new IOException("更新地址校验失败");
        }
        Pattern tagPattern = Pattern.compile("^/" + Pattern.quote(repository) + "/releases/tag/([^/?#]+)$",
                Pattern.CASE_INSENSITIVE);
        Matcher m = tagPattern.matcher(tagUrl.getPath());
        if (!m.matches()) throw new IOException("暂无正式版本");
        String version = m.group(1);
        UpdateRules.compare(version, version);

        List<Asset> assets = githubAssetsFromWeb(repository, version);

        String notes = "";
        try {
            String atom = readText(new URL("https://github.com/" + repository + "/releases.atom"),
                    Collections.singleton("github.com"), false, 1_000_000);
            notes = parseAtomNotes(atom, version);
        } catch (Exception ignored) {}
        return new Release(version, notes, Collections.unmodifiableList(assets));
    }

    static Release latestFromApi(String repository) throws Exception {
        return githubReleaseFromApi(repository, "latest");
    }

    private static Release githubReleaseFromApi(String repository, String releasePath) throws Exception {
        String body = readText(
                new URL("https://api.github.com/repos/" + repository + "/releases/" + releasePath),
                Collections.singleton("api.github.com"), true, 2_000_000);
        return parseRelease(new JSONObject(body), repository);
    }

    private static List<Asset> githubAssetsFromWeb(String repository, String version) throws Exception {
        String html = readText(new URL("https://github.com/" + repository + "/releases/expanded_assets/" + version),
                Collections.singleton("github.com"), false, 1_000_000);
        return parseExpandedAssets(html, repository, version);
    }

    static List<Asset> parseExpandedAssets(String html, String repository, String version) throws Exception {
        List<Asset> assets = new ArrayList<>();
        if (html == null || html.isEmpty()) return assets;
        String expectedPrefix = "/" + repository.toLowerCase(Locale.ROOT)
                + "/releases/download/" + version.toLowerCase(Locale.ROOT) + "/";
        Pattern liPattern = Pattern.compile("<li\\b[^>]*>(.*?)</li>", Pattern.DOTALL);
        Pattern hrefPattern = Pattern.compile("href=\"([^\"]+\\.apk)\"", Pattern.CASE_INSENSITIVE);
        Pattern shaPattern = Pattern.compile("(sha256:[a-fA-F0-9]{64})");
        Matcher li = liPattern.matcher(html);
        while (li.find()) {
            String block = li.group(1);
            Matcher hm = hrefPattern.matcher(block);
            if (!hm.find()) continue;
            String href = hm.group(1);
            URL url = href.startsWith("http") ? new URL(href) : new URL("https://github.com" + href);
            String pathLower = url.getPath().toLowerCase(Locale.ROOT);
            if (!validHost(url, Collections.singleton("github.com")) || !pathLower.startsWith(expectedPrefix)) {
                continue;
            }
            String fileName = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
            if (fileName.isEmpty()) continue;
            String sha = "";
            Matcher sm = shaPattern.matcher(block);
            if (sm.find()) sha = UpdateRules.sha256(sm.group(1));
            assets.add(new Asset(fileName, url, 0L, sha));
        }
        return assets;
    }

    static String parseAtomNotes(String xml, String version) {
        if (xml == null || xml.isEmpty() || version == null || version.isEmpty()) return "";
        Pattern entryPattern = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
        Pattern tagLink = Pattern.compile("/releases/tag/" + Pattern.quote(version) + "[\"<]",
                Pattern.CASE_INSENSITIVE);
        Matcher em = entryPattern.matcher(xml);
        while (em.find()) {
            String entry = em.group(1);
            if (!tagLink.matcher(entry).find()) continue;
            Matcher cm = Pattern.compile("<content[^>]*>(.*?)</content>", Pattern.DOTALL).matcher(entry);
            if (!cm.find()) return "";
            String decoded = unescapeEntities(cm.group(1));
            String normalized = decoded
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)<li[^>]*>", "• ")
                    .replaceAll("(?i)</li>", "\n")
                    .replaceAll("(?i)</(p|div|h[1-6]|ul|ol)>", "\n")
                    .replaceAll("<[^>]+>", "");
            String plain = unescapeEntities(normalized)
                    .replace("\r", "")
                    .replaceAll("[ \\t]+\\n", "\n")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();
            return plain.length() > 1400 ? plain.substring(0, 1400) + "…" : plain;
        }
        return "";
    }

    private static String unescapeEntities(String s) {
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
    }

    static Release parseRelease(JSONObject json, String repository) throws Exception {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) throw new IOException("暂无正式版本");
        String version = json.getString("tag_name");
        UpdateRules.compare(version, version);
        String notes = json.isNull("body") ? "" : json.optString("body", "");
        if (notes.length() > 1400) notes = notes.substring(0, 1400) + "…";
        JSONArray array = json.optJSONArray("assets");
        List<Asset> assets = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.getJSONObject(i);
            String name = value.getString("name");
            long size = value.optLong("size");
            if (!name.toLowerCase(Locale.ROOT).endsWith(".apk") || size <= 0 || size > MAX_APK) continue;
            URL url = new URL(value.getString("browser_download_url"));
            String prefix = "/" + repository.toLowerCase(Locale.ROOT) + "/releases/download/"
                    + version.toLowerCase(Locale.ROOT) + "/";
            if (!validHost(url, Collections.singleton("github.com")) || !url.getPath().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                throw new IOException("安装包下载地址校验不通过");
            }
            String digest = value.isNull("digest") ? "" : value.optString("digest", "");
            assets.add(new Asset(name, url, size, UpdateRules.sha256(digest)));
        }
        return new Release(version, notes, Collections.unmodifiableList(assets));
    }

    static void downloadUpdate(Context context, Asset asset, String release) throws Exception {
        boolean fromGitee = "gitee.com".equals(asset.url.getHost());
        if (!asset.name.equals("FocusGarden-" + release + ".apk")) {
            download(context, asset, release, GITHUB_DOWNLOAD_HOSTS);
            return;
        }
        trySources(() -> {
            Asset domestic = fromGitee ? asset : giteeAsset(release, asset);
            download(context, domestic, release, GITEE_DOWNLOAD_HOSTS);
            return null;
        }, () -> {
            download(context, fromGitee ? githubAsset(release, asset.name) : asset, release, GITHUB_DOWNLOAD_HOSTS);
            return null;
        });
    }

    private static Asset giteeAsset(String version, Asset github) throws Exception {
        if (!github.sha.isEmpty()) {
            return new Asset(github.name, giteeDownloadUrl(version, github.name), github.size, github.sha);
        }
        Release gitee = giteeRelease("tags/" + version);
        if (!version.equals(gitee.version)) throw new IOException("国内更新源版本不匹配");
        return gitee.assets.get(0);
    }

    private static Asset githubAsset(String release, String name) throws Exception {
        try {
            return findAsset(githubAssetsFromWeb(DEFAULT_REPOSITORY, release), name);
        } catch (Exception webError) {
            Release github = githubReleaseFromApi(DEFAULT_REPOSITORY, "tags/" + release);
            if (!release.equals(github.version)) throw new IOException("GitHub 备用源版本不匹配");
            return findAsset(github.assets, name);
        }
    }

    private static Asset findAsset(List<Asset> assets, String name) throws IOException {
        for (Asset asset : assets) {
            if (name.equals(asset.name)) return asset;
        }
        throw new IOException("GitHub 备用源暂无同版本安装包");
    }

    private static void download(Context context, Asset asset, String release, Set<String> hosts) throws Exception {
        File temp = File.createTempFile("focus-update-", ".apk", context.getCacheDir());
        try {
            HttpsURLConnection connection = connect(asset.url, hosts, false);
            try {
                checkHttp(connection);
                long contentLen = connection.getContentLengthLong();
                try (InputStream input = connection.getInputStream()) {
                    copyVerified(input, temp, asset, contentLen);
                }
            } finally {
                connection.disconnect();
            }
            stage(context, temp, release);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    static void copyVerified(InputStream input, File output, Asset asset, long contentLength) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long expected = asset.size > 0 ? asset.size : contentLength;
        if (expected > MAX_APK) throw new IOException("安装包大小超出限制");
        long total = 0;
        try (FileOutputStream destination = new FileOutputStream(output)) {
            byte[] buffer = new byte[32768];
            int length;
            while ((length = input.read(buffer)) != -1) {
                total += length;
                if (total > MAX_APK || (expected > 0 && total > expected)) {
                    throw new IOException("安装包大小与校验信息不符");
                }
                digest.update(buffer, 0, length);
                destination.write(buffer, 0, length);
            }
        }
        if (expected > 0 && total != expected) throw new IOException("安装包下载不完整");
        if (total <= 0) throw new IOException("安装包下载失败");
        if (!asset.sha.isEmpty() && !asset.sha.equals(hex(digest.digest()))) {
            throw new IOException("安装包文件指纹校验失败");
        }
    }

    static void stage(Context context, File apk, String release) throws Exception {
        PackageInfo archive = validate(context, apk);
        if (UpdateRules.compare(archive.versionName, release) != 0) throw new IOException("安装包版本与发布标签不一致");
        Files.move(apk.toPath(), pending(context).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        boolean saved = context.getSharedPreferences("github_updates", Context.MODE_PRIVATE).edit()
                .putLong("downloadedCode", code(archive)).commit();
        if (!saved) throw new IOException("无法保存下载状态");
    }

    static PackageInfo validate(Context context, File apk) throws IOException {
        PackageInfo current = installed(context);
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), flags());
        if (archive == null || !context.getPackageName().equals(archive.packageName)) {
            throw new IOException("这不是留白专注的安装包");
        }
        Set<Signature> expected = signers(current);
        if (expected.isEmpty() || !expected.equals(signers(archive))) throw new IOException("安装包签名与当前版本不同");
        if (code(archive) <= code(current)) throw new IOException("新版本构建号必须比当前版本大");
        if (archive.applicationInfo != null && archive.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) {
            throw new IOException("此版本需要更新的 Android 系统");
        }
        return archive;
    }

    private static int flags() {
        return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    private static long code(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static Set<Signature> signers(PackageInfo info) {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
                ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        return signatures == null ? Collections.emptySet() : new HashSet<>(Arrays.asList(signatures));
    }

    private static String readText(URL endpoint, Set<String> hosts, boolean api, int maxBytes) throws IOException {
        HttpsURLConnection connection = connect(endpoint, hosts, api);
        try {
            checkHttp(connection);
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (content.size() + length > maxBytes) throw new IOException("版本信息过大");
                    content.write(buffer, 0, length);
                }
            }
            return new String(content.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpsURLConnection openSingle(URL url, Set<String> hosts, boolean api) throws IOException {
        if (!validHost(url, hosts)) throw new IOException("更新地址无效");
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(GITEE_DOWNLOAD_HOSTS.contains(url.getHost()) ? 5000 : 12000);
        c.setReadTimeout(url.getPath().startsWith("/api/v5/") ? 6000 : 16000);
        c.setRequestProperty("User-Agent", "FocusGarden-Android");
        if (api) {
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        }
        return c;
    }

    private static HttpsURLConnection connect(URL target, Set<String> hosts, boolean api) throws IOException {
        URL url = target;
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpsURLConnection c = openSingle(url, hosts, api);
            try {
                int status = c.getResponseCode();
                if (status < 300 || status >= 400) return c;
                String location = c.getHeaderField("Location");
                if (location == null) throw new IOException("更新跳转信息无效");
                url = new URL(url, location);
            } catch (IOException e) {
                c.disconnect();
                throw e;
            }
            c.disconnect();
        }
        throw new IOException("更新跳转次数过多");
    }

    private static boolean validHost(URL url, Set<String> hosts) {
        return "https".equals(url.getProtocol()) && url.getUserInfo() == null
                && (url.getPort() == -1 || url.getPort() == 443)
                && hosts.contains(url.getHost().toLowerCase(Locale.ROOT));
    }

    private static void checkHttp(HttpsURLConnection c) throws IOException {
        checkStatus(c.getResponseCode());
    }

    private static void checkStatus(int status) throws IOException {
        if (status == 200) return;
        if (status == 404) throw new IOException("暂无可用的正式版本");
        if (status == 403 || status == 429) throw new IOException("更新服务暂时繁忙，请稍后重试");
        throw new IOException("检查更新失败（" + status + "）");
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(2 * bytes.length);
        for (byte value : bytes) {
            result.append(Character.forDigit((value & 255) >>> 4, 16))
                  .append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }
}
