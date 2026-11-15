package cn.kylebing.bbq10wubi.sync;

import org.json.JSONObject;

public final class WubiCloudUserInfo {
    public final int uid;
    public final String email;
    public final String nickname;
    public final String token;
    public final String avatar;

    public WubiCloudUserInfo(int uid, String email, String nickname, String token, String avatar) {
        this.uid = uid;
        this.email = email == null ? "" : email;
        this.nickname = nickname == null ? "" : nickname;
        this.token = token == null ? "" : token;
        this.avatar = avatar == null ? "" : avatar;
    }

    public boolean isLoggedIn() {
        return uid > 0 && token.length() > 0;
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("uid", uid);
            o.put("email", email);
            o.put("nickname", nickname);
            o.put("token", token);
            o.put("avatar", avatar);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static WubiCloudUserInfo fromJson(String raw) {
        try {
            JSONObject o = new JSONObject(raw);
            String token = o.optString("token");
            WubiCloudUserInfo info = new WubiCloudUserInfo(
                    o.optInt("uid"),
                    o.optString("email"),
                    o.optString("nickname"),
                    token,
                    o.optString("avatar"));
            return info.isLoggedIn() ? info : null;
        } catch (Exception e) {
            return null;
        }
    }

    public WubiCloudUserInfo withToken(String newToken) {
        return new WubiCloudUserInfo(uid, email, nickname, newToken, avatar);
    }

    public WubiCloudUserInfo withAvatar(String newAvatar) {
        return new WubiCloudUserInfo(uid, email, nickname, token, newAvatar);
    }
}
