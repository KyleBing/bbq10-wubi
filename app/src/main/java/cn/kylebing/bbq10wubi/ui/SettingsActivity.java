package cn.kylebing.bbq10wubi.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import cn.kylebing.bbq10wubi.Prefs;
import cn.kylebing.bbq10wubi.R;
import cn.kylebing.bbq10wubi.sync.UserDictAutoSync;
import cn.kylebing.bbq10wubi.sync.WubiCloudConfig;

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        HomeNav.bind(this);

        CheckBox four = (CheckBox) findViewById(R.id.chkFourUnique);
        CheckBox empty = (CheckBox) findViewById(R.id.chkEmptyClear);
        CheckBox enter = (CheckBox) findViewById(R.id.chkEnterClear);
        CheckBox comments = (CheckBox) findViewById(R.id.chkComments);
        CheckBox auto = (CheckBox) findViewById(R.id.chkAutoSync);
        final TextView syncHint = (TextView) findViewById(R.id.syncHint);
        TextView version = (TextView) findViewById(R.id.versionText);

        four.setChecked(Prefs.autoSelectUniqueFourCode(this));
        empty.setChecked(Prefs.clearEmptyCode(this));
        enter.setChecked(Prefs.enterClearsCode(this));
        comments.setChecked(Prefs.showComment(this));
        auto.setChecked(Prefs.autoSync(this));
        refreshHint(syncHint);
        version.setText(appVersion());

        four.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setAutoSelectUniqueFourCode(SettingsActivity.this, isChecked);
            }
        });
        empty.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setClearEmptyCode(SettingsActivity.this, isChecked);
            }
        });
        enter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setEnterClearsCode(SettingsActivity.this, isChecked);
            }
        });
        comments.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setShowComment(SettingsActivity.this, isChecked);
            }
        });
        auto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setAutoSync(SettingsActivity.this, isChecked);
                if (isChecked && WubiCloudConfig.isLoggedIn(SettingsActivity.this)) {
                    UserDictAutoSync.syncSoon(SettingsActivity.this);
                }
                refreshHint(syncHint);
            }
        });

        findViewById(R.id.btnAccount).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, AccountActivity.class));
            }
        });
        findViewById(R.id.btnHelp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, HelpActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHint((TextView) findViewById(R.id.syncHint));
    }

    private void refreshHint(TextView view) {
        String last = UserDictAutoSync.lastResult(this);
        if (!WubiCloudConfig.isLoggedIn(this)) {
            view.setText("未登录。注册：kylebing.cn/wubi/manager/#/register");
        } else if (last != null) {
            view.setText(last);
        } else {
            view.setText("已登录 " + WubiCloudConfig.userInfo(this).email);
        }
    }

    private String appVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return "v" + info.versionName + "  ·  " + info.packageName;
        } catch (Exception e) {
            return "";
        }
    }
}
