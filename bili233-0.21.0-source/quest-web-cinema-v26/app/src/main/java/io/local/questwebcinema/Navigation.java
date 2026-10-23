package io.local.questwebcinema;

import java.net.URI;
import java.net.URLEncoder;

/** Pure Java policy, shared by address input and WebView top-level navigation. */
public final class Navigation {
    public static final String HOME = "https://www.bilibili.com/";
    private Navigation() {}

    public static boolean isBili(String text) {
        try {
            URI uri = new URI(text);
            String host = uri.getHost();
            if (host == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1) return false;
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.equals("bilibili.com") || host.endsWith(".bilibili.com") || host.equals("b23.tv");
        } catch (Exception e) { return false; }
    }

    public static boolean isHttps(String text) {
        try {
            URI uri = new URI(text);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception e) { return false; }
    }
    public static boolean isWeb(String text) {
        try { URI u=new URI(text);return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null; }
        catch(Exception ignored){return false;}
    }

    public static String resolve(String text) {
        String value = text.trim();
        if (value.isEmpty()) return HOME;
        if (value.matches("(?i)BV[0-9a-z]{10}")) return HOME + "video/" + value + "/";
        if (!value.contains(":") && value.matches("(?i)[a-z0-9.-]+\\.[a-z]{2,}([/?#].*)?")) value = "https://" + value;
        if (value.contains("://") || value.startsWith("javascript:") || value.startsWith("intent:") || value.startsWith("file:")) {
            if (!isWeb(value)) throw new IllegalArgumentException("请输入 HTTPS 网址，或直接输入视频搜索词。");
            return value;
        }
        try { return "https://search.bilibili.com/all?keyword=" + URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
}
