package cn.kylebing.bbq10wubi.sync;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public final class WubiCloudApi {
    private WubiCloudApi() {}

    public static final class PullResult {
        public final String title;
        public final String yaml;
        public final int revision;

        public PullResult(String title, String yaml, int revision) {
            this.title = title;
            this.yaml = yaml;
            this.revision = revision;
        }
    }

    public static final class DictMeta {
        public final int revision;

        public DictMeta(int revision) {
            this.revision = revision;
        }
    }

    public static final class ExtraWord {
        public final String code;
        public final String word;
        public final int weight;
        public final String comment;
        public final String categoryName;
        public final int categoryId;
        public final int id;

        public ExtraWord(String code, String word, int weight, String comment,
                         String categoryName, int categoryId, int id) {
            this.code = code;
            this.word = word;
            this.weight = weight;
            this.comment = comment;
            this.categoryName = categoryName;
            this.categoryId = categoryId;
            this.id = id;
        }
    }

    public static WubiCloudUserInfo login(Context c, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        String raw = request(c, "POST", url(c, "user/login"), body, null);
        JSONObject root = new JSONObject(raw);
        if (!root.optBoolean("success")) {
            JSONObject data = root.optJSONObject("data");
            if (data != null && "email_not_verified".equals(data.optString("code"))) {
                throw new CloudException(root.optString("message", "邮箱尚未验证，请查收验证邮件后再登录"));
            }
            throw new CloudException(root.optString("message", "登录失败"));
        }
        JSONObject payload = root.optJSONObject("data");
        if (payload == null) throw new CloudException("登录失败");
        String token = payload.optString("token");
        if (token.length() == 0) throw new CloudException("登录失败：未返回 token");
        WubiCloudUserInfo info = new WubiCloudUserInfo(
                payload.optInt("uid"),
                emptyTo(payload.optString("email"), email),
                payload.optString("nickname"),
                token,
                payload.optString("avatar"));
        WubiCloudConfig.setUserInfo(c, info);
        return info;
    }

    public static PullResult pullDict(Context c) throws Exception {
        WubiCloudUserInfo u = requireUser(c);
        String raw = request(c, "GET", url(c, "dict/pull") + "?title=" + enc(WubiCloudConfig.SYNC_DICT), null, u);
        JSONObject root = new JSONObject(raw);
        if (!root.optBoolean("success")) {
            String msg = root.optString("message");
            if (msg.contains("不存在") || msg.contains("未") || msg.toLowerCase().contains("not found")) {
                return null;
            }
            throw new CloudException(msg.length() == 0 ? "拉取失败" : msg);
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) return null;
        String yaml = data.optString("content");
        if (yaml.length() == 0) return null;
        return new PullResult(data.optString("title", WubiCloudConfig.SYNC_DICT), yaml, data.optInt("revision"));
    }

    public static DictMeta fetchMeta(Context c) throws Exception {
        WubiCloudUserInfo u = requireUser(c);
        String raw = request(c, "GET", url(c, "dict/meta") + "?title=" + enc(WubiCloudConfig.SYNC_DICT), null, u);
        JSONObject root = new JSONObject(raw);
        if (!root.optBoolean("success")) {
            String msg = root.optString("message");
            if (msg.contains("不存在") || msg.contains("未")) return null;
            throw new CloudException(msg.length() == 0 ? "读取版本失败" : msg);
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) return null;
        int revision = data.optInt("revision");
        if (revision <= 0) return null;
        return new DictMeta(revision);
    }

    public static int pushDict(Context c, String yaml, int wordCount, Integer baseRevision) throws Exception {
        WubiCloudUserInfo u = requireUser(c);
        if (yaml.length() >= WubiCloudConfig.SYNC_MAX) {
            throw new CloudException("同步内容超过 " + WubiCloudConfig.SYNC_MAX + " 字");
        }
        JSONObject body = new JSONObject();
        body.put("title", WubiCloudConfig.SYNC_DICT);
        body.put("content", yaml);
        body.put("contentSize", yaml.length());
        body.put("wordCount", wordCount);
        if (baseRevision != null) body.put("baseRevision", baseRevision.intValue());
        String raw = request(c, "PUT", url(c, "dict/push"), body, u);
        JSONObject root = new JSONObject(raw);
        JSONObject data = root.optJSONObject("data");
        if ((data != null && data.optBoolean("conflict")) || root.optString("message").contains("版本已变化")) {
            throw new CloudException("版本已变化");
        }
        if (!root.optBoolean("success")) throw new CloudException(root.optString("message", "上传失败"));
        WubiCloudConfig.setLastSyncAt(c, System.currentTimeMillis());
        return data == null ? 0 : data.optInt("revision");
    }

    public static List<ExtraWord> pullExtraDict(Context c) throws Exception {
        WubiCloudUserInfo u = requireUser(c);
        String raw = request(c, "POST", url(c, "word/export-extra"), new JSONObject(), u);
        JSONObject root = new JSONObject(raw);
        if (!root.optBoolean("success")) throw new CloudException(root.optString("message", "拉取扩展词库失败"));
        JSONArray arr = root.optJSONArray("data");
        ArrayList<ExtraWord> out = new ArrayList<ExtraWord>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int weight = 0;
            Object priority = o.opt("priority");
            if (priority instanceof Number) weight = ((Number) priority).intValue();
            else if (priority instanceof String) {
                try {
                    weight = Integer.parseInt(((String) priority).trim());
                } catch (NumberFormatException ignored) {
                }
            } else {
                weight = o.optInt("priority");
            }
            out.add(new ExtraWord(
                    o.optString("code"),
                    o.optString("word"),
                    weight,
                    o.optString("comment"),
                    o.optString("category_name"),
                    o.optInt("category_id"),
                    o.optInt("id")));
        }
        return out;
    }

    private static WubiCloudUserInfo requireUser(Context c) throws CloudException {
        WubiCloudUserInfo u = WubiCloudConfig.userInfo(c);
        if (u == null || !u.isLoggedIn()) throw new CloudException("未登录，请先登录");
        return u;
    }

    private static String url(Context c, String path) {
        if (path.startsWith("/")) path = path.substring(1);
        return WubiCloudConfig.baseURL(c) + path;
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String emptyTo(String v, String fallback) {
        return v == null || v.length() == 0 ? fallback : v;
    }

    private static String request(Context c, String method, String urlStr, JSONObject json,
                                  WubiCloudUserInfo user) throws Exception {
        byte[] bytes = json == null ? null : json.toString().getBytes("UTF-8");
        String auth = user == null ? null : "Bearer " + user.token;
        LegacyHttps.Response r = LegacyHttps.execute(method, urlStr, bytes, auth);
        if (r.accessToken != null && r.accessToken.length() > 0) {
            WubiCloudConfig.updateTokenIfNeeded(c, r.accessToken);
        }
        if (r.code < 200 || r.code >= 300) throw new CloudException("网络错误（HTTP " + r.code + "）");
        return r.body;
    }
}
