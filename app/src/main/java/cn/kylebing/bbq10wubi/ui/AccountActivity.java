package cn.kylebing.bbq10wubi.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import cn.kylebing.bbq10wubi.R;
import cn.kylebing.bbq10wubi.WubiApp;
import cn.kylebing.bbq10wubi.data.UserDictDocument;
import cn.kylebing.bbq10wubi.data.UserDictWriter;
import cn.kylebing.bbq10wubi.sync.DictSyncDiff;
import cn.kylebing.bbq10wubi.sync.ExtraDictUpdater;
import cn.kylebing.bbq10wubi.sync.UserDictAutoSync;
import cn.kylebing.bbq10wubi.sync.LegacyHttps;
import cn.kylebing.bbq10wubi.sync.WubiCloudApi;
import cn.kylebing.bbq10wubi.sync.WubiCloudConfig;
import cn.kylebing.bbq10wubi.sync.WubiCloudUserInfo;

public class AccountActivity extends Activity {
    private EditText email;
    private EditText password;
    private TextView status;
    private TextView log;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        email = (EditText) findViewById(R.id.email);
        password = (EditText) findViewById(R.id.password);
        status = (TextView) findViewById(R.id.accountStatus);
        log = (TextView) findViewById(R.id.syncLog);
        refreshStatus();

        WubiCloudUserInfo u = WubiCloudConfig.userInfo(this);
        if (u != null) email.setText(u.email);

        findViewById(R.id.btnLogin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLogin();
            }
        });
        findViewById(R.id.btnLogout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WubiCloudConfig.logout(AccountActivity.this);
                refreshStatus();
                toast("已退出");
            }
        });
        wire(R.id.btnUpload, new Runnable() {
            @Override
            public void run() {
                confirm("上传会覆盖云端用户词，继续？", new Runnable() {
                    @Override
                    public void run() {
                        background("正在上传…", new Work() {
                            @Override
                            public String run() throws Exception {
                                UserDictDocument local = UserDictWriter.loadActiveDocument(AccountActivity.this);
                                int rev = WubiCloudApi.pushDict(AccountActivity.this,
                                        UserDictWriter.exportText(AccountActivity.this),
                                        local.entryCount(), null);
                                UserDictAutoSync.noteCloudRevision(AccountActivity.this, rev, local);
                                return "已上传覆盖云端（" + local.entryCount() + " 条）";
                            }
                        });
                    }
                });
            }
        });
        wire(R.id.btnDownload, new Runnable() {
            @Override
            public void run() {
                confirm("下载会覆盖本地用户词，继续？", new Runnable() {
                    @Override
                    public void run() {
                        background("正在下载…", new Work() {
                            @Override
                            public String run() throws Exception {
                                WubiCloudApi.PullResult pulled = WubiCloudApi.pullDict(AccountActivity.this);
                                if (pulled == null) return "云端尚无备份";
                                UserDictWriter.importText(AccountActivity.this, pulled.yaml);
                                UserDictDocument doc = UserDictWriter.parseDocument(pulled.yaml);
                                UserDictAutoSync.noteCloudRevision(AccountActivity.this, pulled.revision, doc);
                                reloadDict();
                                return "已覆盖本地（" + doc.entryCount() + " 条）";
                            }
                        });
                    }
                });
            }
        });
        wire(R.id.btnMerge, new Runnable() {
            @Override
            public void run() {
                background("正在合并…", new Work() {
                    @Override
                    public String run() throws Exception {
                        WubiCloudApi.PullResult pulled = WubiCloudApi.pullDict(AccountActivity.this);
                        UserDictDocument local = UserDictWriter.loadActiveDocument(AccountActivity.this);
                        if (pulled == null) {
                            int rev = WubiCloudApi.pushDict(AccountActivity.this,
                                    UserDictWriter.exportText(AccountActivity.this),
                                    local.entryCount(), Integer.valueOf(0));
                            UserDictAutoSync.noteCloudRevision(AccountActivity.this, rev, local);
                            return "云端无备份，已上传本地 " + local.entryCount() + " 条";
                        }
                        UserDictDocument remote = UserDictWriter.parseDocument(pulled.yaml);
                        UserDictDocument merged = DictSyncDiff.mergeRemoteIntoLocal(local, remote);
                        UserDictWriter.saveActiveDocument(AccountActivity.this, merged);
                        int rev = WubiCloudApi.pushDict(AccountActivity.this,
                                UserDictWriter.exportText(AccountActivity.this),
                                merged.entryCount(), Integer.valueOf(pulled.revision));
                        UserDictAutoSync.noteCloudRevision(AccountActivity.this, rev, merged);
                        reloadDict();
                        return "已合并并上传（" + merged.entryCount() + " 条）";
                    }
                });
            }
        });
        wire(R.id.btnCompare, new Runnable() {
            @Override
            public void run() {
                background("正在对比…", new Work() {
                    @Override
                    public String run() throws Exception {
                        UserDictDocument local = UserDictWriter.loadActiveDocument(AccountActivity.this);
                        WubiCloudApi.PullResult pulled = WubiCloudApi.pullDict(AccountActivity.this);
                        if (pulled == null) {
                            return DictSyncDiff.compare(DictSyncDiff.flatten(local), null, false).summary();
                        }
                        UserDictDocument remote = UserDictWriter.parseDocument(pulled.yaml);
                        return DictSyncDiff.compare(DictSyncDiff.flatten(local), DictSyncDiff.flatten(remote), true).summary();
                    }
                });
            }
        });
        wire(R.id.btnExtra, new Runnable() {
            @Override
            public void run() {
                background("正在拉取扩展词库…", new Work() {
                    @Override
                    public String run() throws Exception {
                        ExtraDictUpdater.ApplyResult r = ExtraDictUpdater.applyOnlineItems(
                                AccountActivity.this, WubiCloudApi.pullExtraDict(AccountActivity.this));
                        WubiApp app = (WubiApp) getApplication();
                        app.reloadOverlay();
                        return "扩展词库已覆盖：共 " + r.wordCount + " 条，新增 " + r.added + "，分组 " + r.groupCount;
                    }
                });
            }
        });
    }

    private void wire(int id, final Runnable action) {
        ((Button) findViewById(id)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!WubiCloudConfig.isLoggedIn(AccountActivity.this)) {
                    toast("请先登录");
                    return;
                }
                action.run();
            }
        });
    }

    private void doLogin() {
        final String mail = email.getText().toString().trim();
        final String pass = password.getText().toString();
        if (mail.length() == 0 || pass.length() == 0) {
            toast("请填写邮箱和密码");
            return;
        }
        background("正在登录…", new Work() {
            @Override
            public String run() throws Exception {
                WubiCloudUserInfo info = WubiCloudApi.login(AccountActivity.this, mail, pass);
                UserDictAutoSync.syncSoon(AccountActivity.this);
                return "已登录 " + info.email;
            }
        });
    }

    private void confirm(String msg, final Runnable ok) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton("继续", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ok.run();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private interface Work {
        String run() throws Exception;
    }

    private void background(String pending, final Work work) {
        log.setText(pending);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String msg = work.run();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            log.setText(msg);
                            refreshStatus();
                            toast(msg);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            String m = LegacyHttps.friendlyError(e);
                            log.setText(m);
                            toast(m);
                        }
                    });
                }
            }
        }).start();
    }

    private void reloadDict() {
        ((WubiApp) getApplication()).reloadUserDict();
    }

    private void refreshStatus() {
        WubiCloudUserInfo u = WubiCloudConfig.userInfo(this);
        if (u == null) status.setText("未登录");
        else status.setText("已登录 " + u.email + (u.nickname.length() == 0 ? "" : "（" + u.nickname + "）"));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
