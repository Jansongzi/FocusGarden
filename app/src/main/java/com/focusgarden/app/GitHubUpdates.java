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

import javax.net.ssl.HttpsURLConnection;

final class GitHubUpdates {
    static final String MIME = "application/vnd.android.package-archive";
    private static final String DEFAULT_REPOSITORY = "Jansongzi/FocusGarden";
    private static final long MAX_APK = 150L * 1024 * 1024;
    private static final Set<String> DOWNLOAD_HOSTS = new HashSet<>(Arrays.asList("github.com",
            "objects.githubusercontent.com", "release-assets.githubusercontent.com", "github-releases.githubusercontent.com"));

    record Asset(String name, URL url, long size, String sha) {}
    record Release(String version, String notes, List<Asset> assets) {}

    static PackageInfo installed(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), flags());
        } catch (PackageManager.NameNotFoundException impossible) {
            throw new IllegalStateException("找不到已安装版本", impossible);
        }
    }

    static File pending(Context context) {
        return new File(context.getCacheDir(), "focus-garden-update.apk");
    }

    static Uri installUri(Context context) {
        return Uri.parse("content://" + context.getPackageName() + ".updates/update.apk");
    }

    static String repository(Context context) {
        return context.getSharedPreferences("github_updates", Context.MODE_PRIVATE)
                .getString("repository", DEFAULT_REPOSITORY);
    }

    static void setRepository(Context context, String repository) throws IOException {
        if (repository.equals(repository(context))) return;
        context.revokeUriPermission(installUri(context), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Files.deleteIfExists(pending(context).toPath());
        boolean saved = context.getSharedPreferences("github_updates", Context.MODE_PRIVATE).edit()
                .putString("repository", repository).remove("downloadedCode").commit();
        if (!saved) throw new IOException("无法保存 GitHub 更新源");
    }

    static boolean hasPending(Context context, PackageInfo current) {
        long downloaded = context.getSharedPreferences("github_updates", Context.MODE_PRIVATE).getLong("downloadedCode", 0);
        return downloaded > code(current) && pending(context).isFile();
    }

    static Release latest(String repository) throws Exception {
        URL endpoint = new URL("https://api.github.com/repos/" + repository + "/releases/latest");
        HttpsURLConnection connection = connect(endpoint, true);
        try {
            checkHttp(connection);
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (content.size() + length > 2_000_000) throw new IOException("版本信息过大");
                    content.write(buffer, 0, length);
                }
            }
            return parseRelease(new JSONObject(new String(content.toByteArray(), StandardCharsets.UTF_8)), repository);
        } finally {
            connection.disconnect();
        }
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
                throw new IOException("APK 下载地址和更新源不匹配");
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
                try (InputStream input = connection.getInputStream()) {
                    copyVerified(input, temp, asset);
                }
            } finally {
                connection.disconnect();
            }
            stage(context, temp, release);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    static void copyVerified(InputStream input, File output, Asset asset) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (FileOutputStream destination = new FileOutputStream(output)) {
            byte[] buffer = new byte[32768];
            int length;
            while ((length = input.read(buffer)) != -1) {
                total += length;
                if (total > asset.size) throw new IOException("APK 大小与 Release 附件不符");
                digest.update(buffer, 0, length);
                destination.write(buffer, 0, length);
            }
        }
        if (total != asset.size) throw new IOException("APK 下载不完整");
        if (!asset.sha.isEmpty() && !asset.sha.equals(hex(digest.digest()))) {
            throw new IOException("下载文件的 SHA-256 校验失败");
        }
    }

    static void stage(Context context, File apk, String release) throws Exception {
        PackageInfo archive = validate(context, apk);
        if (UpdateRules.compare(archive.versionName, release) != 0) throw new IOException("APK 版本与 Release 标签不一致");
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
        if (expected.isEmpty() || !expected.equals(signers(archive))) throw new IOException("APK 签名与当前安装版本不同");
        if (code(archive) <= code(current)) throw new IOException("APK 构建号必须比当前版本大");
        if (archive.applicationInfo != null && archive.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) {
            throw new IOException("此 APK 需要更新的 Android 版本");
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

    private static HttpsURLConnection connect(URL target, boolean api) throws IOException {
        Set<String> hosts = api ? Collections.singleton("api.github.com") : DOWNLOAD_HOSTS;
        URL url = target;
        for (int redirects = 0; redirects < 5; redirects++) {
            if (!validHost(url, hosts)) throw new IOException("GitHub 下载地址无效");
            HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(12000);
            c.setReadTimeout(16000);
            c.setRequestProperty("User-Agent", "FocusGarden-Android");
            if (api) {
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
            }
            try {
                int status = c.getResponseCode();
                if (status < 300 || status >= 400) return c;
                String location = c.getHeaderField("Location");
                if (location == null) throw new IOException("GitHub 返回了无效的跳转");
                url = new URL(url, location);
            } catch (IOException e) {
                c.disconnect();
                throw e;
            }
            c.disconnect();
        }
        throw new IOException("GitHub 跳转次数过多");
    }

    private static boolean validHost(URL url, Set<String> hosts) {
        return "https".equals(url.getProtocol()) && url.getUserInfo() == null
                && (url.getPort() == -1 || url.getPort() == 443)
                && hosts.contains(url.getHost().toLowerCase(Locale.ROOT));
    }

    private static void checkHttp(HttpsURLConnection c) throws IOException {
        int status = c.getResponseCode();
        if (status == 200) return;
        if (status == 404) throw new IOException("找不到公开仓库或正式 Release（404）");
        if (status == 403 || status == 429) throw new IOException("GitHub 暂时限制访问，请稍后重试");
        throw new IOException("GitHub 请求失败（" + status + "）");
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
