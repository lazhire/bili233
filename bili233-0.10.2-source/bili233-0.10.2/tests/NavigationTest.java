import io.local.questwebcinema.Navigation;

public final class NavigationTest {
    private static int cases;
    private static void check(boolean condition, String name) {
        cases++;
        if (!condition) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        check(Navigation.isBili("https://www.bilibili.com/video/BV1234567890/"), "official website");
        check(Navigation.isBili("https://passport.bilibili.com/login"), "login subdomain");
        check(Navigation.isBili("https://b23.tv/abc"), "short links");
        check(!Navigation.isBili("https://bilibili.com.evil.test/"), "suffix phishing");
        check(!Navigation.isBili("https://evilbilibili.com/"), "prefix phishing");
        check(!Navigation.isBili("https://user:pass@bilibili.com/"), "credentials rejected");
        check(!Navigation.isBili("http://bilibili.com/"), "cleartext rejected");
        check(!Navigation.isBili("https://bilibili.com:8443/"), "custom port rejected");
        check(!Navigation.isBili("intent://video"), "deep links rejected");
        check(!Navigation.isHttps("javascript:alert(1)"), "external javascript rejected");
        check(Navigation.resolve(" ").equals(Navigation.HOME), "empty input");
        check(Navigation.resolve("BV1234567890").equals("https://www.bilibili.com/video/BV1234567890/"), "BV navigation");
        check(Navigation.resolve("b23.tv/abc").equals("https://b23.tv/abc"), "short link normalization");
        check(Navigation.resolve("猫 & VR").endsWith("%E7%8C%AB+%26+VR"), "search query encoding");
        boolean rejected = false;
        try { Navigation.resolve("https://evil.test/"); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "address bar restriction");
        System.out.println("PASS: " + cases + " navigation cases");
    }
}
