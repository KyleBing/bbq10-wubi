package cn.kylebing.bbq10wubi.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import cn.kylebing.bbq10wubi.R;

final class HomeNav {
    private HomeNav() {}

    static void bind(final Activity activity) {
        View home = activity.findViewById(R.id.btnHome);
        if (home == null) return;
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, ComposeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                activity.finish();
            }
        });
    }
}
