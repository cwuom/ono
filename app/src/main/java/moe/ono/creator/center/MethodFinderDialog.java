package moe.ono.creator.center;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.lxj.xpopup.animator.PopupAnimator;
import com.lxj.xpopup.core.CenterPopupView;

import moe.ono.R;
import moe.ono.dexkit.TargetManager;
import moe.ono.hooks.base.util.Toasts;

@SuppressLint("ViewConstructor")
public class MethodFinderDialog extends CenterPopupView {
    private final Activity activity;
    private final ClassLoader cl;
    private final ApplicationInfo ai;
    private boolean flag = false;

    public MethodFinderDialog(@NonNull Activity activity, @NonNull ClassLoader cl, @NonNull ApplicationInfo ai) {
        super(activity);
        this.activity = activity;
        this.cl = cl;
        this.ai = ai;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.methodfinder_layout;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        findViewById(R.id.btn_close).setOnClickListener(v -> {
            dismiss();
            if (flag) {
                activity.finish();
                stopAllServices(activity);
            }
        });

        findViewById(R.id.btn_find_method).setOnClickListener(v -> {
            postDelayed(() -> {
                Toasts.info(activity,"开始查找...");
                findViewById(R.id.btn_find_method).setVisibility(GONE);
                Button button = findViewById(R.id.btn_close);
                button.setVisibility(GONE);
                TextView textView = findViewById(R.id.tv_tip);
                ProgressBar progressBar = findViewById(R.id.progress_bar);
                progressBar.setVisibility(View.VISIBLE);
                TargetManager.runMethodFinder(ai,cl,activity,
                    result -> {
                        textView.setText(result);
                        button.setText("重启 QQ");
                        button.setVisibility(VISIBLE);
                        flag = true;
                        Toasts.success(activity,"完成");
                        progressBar.setVisibility(View.GONE);
                        TargetManager.setIsNeedFindTarget(false);
                    }
                );

            }, 0);
        });
    }

    public static void stopAllServices(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (context.getPackageName().equals(service.service.getPackageName())) {
                    try {
                        context.stopService(Intent.makeMainActivity(service.service));
                    } catch (Exception ignored) {}
                }
            }
        }
    }


    @Override
    protected int getMaxWidth() {
        return super.getMaxWidth();
    }

    @Override
    protected int getMaxHeight() {
        return super.getMaxHeight();
    }

    @Override
    protected PopupAnimator getPopupAnimator() {
        return super.getPopupAnimator();
    }

    protected int getPopupWidth() {
        return 0;
    }

    protected int getPopupHeight() {
        return 0;
    }
}