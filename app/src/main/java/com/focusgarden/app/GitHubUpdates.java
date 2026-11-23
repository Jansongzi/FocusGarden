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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

final class GitHubUpdates {
    static final String MIME = "application/vnd.android.package-archive";
    static final String DEFAULT_REPOSITORY = "Jansongzi/FocusGarden";
    private static final long MAX_APK = 150L * 1024 * 1024;
    private static final Set<String> DOWNLOAD_HOSTS = new HashSet<>(Arrays.asList("github.com",
            "objects.githubusercontent.com", "release-assets.githubusercontent.com", "github-releases.githubusercontent.com"));
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

        String assetsHtml = readText(
                new URL("https://github.com/" + repository + "/releases/expanded_assets/" + version),
                Collections.singleton("github.com"), false, 1_000_000);
        List<Asset> assets = parseExpandedAssets(assetsHtml, repository, version);

        String notes = "";
        try {
            String atom = readText(new URL("https://github.com/" + repository + "/releases.atom"),
                    Collections.singleton("github.com"), false, 1_000_000);
            notes = parseAtomNotes(atom, version);
        } catch (Exception ignored) {}
        return new Release(version, notes, Collections.unmodifiableList(assets));
    }

    static Release latestFromApi(String repository) throws Exception {
        String body = readText(
                new URL("https://api.github.com/repos/" + repository + "/releases/latest"),
                Collections.singleton("api.github.com"), true, 2_000_000);
        return parseRelease(new JSONObject(body), repository);
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
            String prefix = "/" + repository.toLowerCase(Locale.ROOT) + "/releases/download/";
            if (!validHost(url, Collections.singleton("github.com")) || !url.getPath().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                throw new IOException("安装包下载地址校验不通过");
            }
            String digest = value.isNull("digest") ? "" : value.optString("digest", "");
            assets.add(new Asset(name, url, size, UpdateRules.sha256(digest)));
        }
        return new Release(version, notes, Collections.unmodifiableList(assets));
    }

    static void download(Context context, Asset asset, String release) throws Exception {
        File temp = File.createTempFile("focus-update-", ".apk", context.getCacheDir());
        try {
            HttpsURLConnection connection = connect(asset.url, false);
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
        c.setConnectTimeout(12000);
        c.setReadTimeout(16000);
        c.setRequestProperty("User-Agent", "FocusGarden-Android");
        if (api) {
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        }
        return c;
    }

    private static HttpsURLConnection connect(URL target, boolean api) throws IOException {
        return connect(target, api ? Collections.singleton("api.github.com") : DOWNLOAD_HOSTS, api);
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
