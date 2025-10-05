package moe.ono.creator;

import static moe.ono.util.analytics.ActionReporter.reportVisitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import moe.ono.R;
import moe.ono.ui.CommonContextWrapper;
import moe.ono.util.AppRuntimeHelper;

@SuppressLint("ResourceType")
public class JumpSchemeUriDialog extends BottomPopupView {
    private static BasePopupView popupView;

    public JumpSchemeUriDialog(@NonNull Context context) {
        super(context);
    }

    public static void createView(Context context) {
        Context fixContext = CommonContextWrapper.createAppCompatContext(context);
        XPopup.Builder NewPop = new XPopup.Builder(fixContext).moveUpToKeyboard(true).isDestroyOnDismiss(true);
        NewPop.maxHeight((int) (XPopupUtils.getScreenHeight(context) * .7f));
        NewPop.popupHeight((int) (XPopupUtils.getScreenHeight(context) * .63f));


        reportVisitor(AppRuntimeHelper.getAccount(), "CreateView-JumpSchemeUriDialog");

        popupView = NewPop.asCustom(new JumpSchemeUriDialog(fixContext));
        popupView.show();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate() {
        super.onCreate();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Button btn_jump = findViewById(R.id.btn_jump);
            EditText scheme_uri = findViewById(R.id.scheme_uri);

            scheme_uri.setVisibility(VISIBLE);
            scheme_uri.clearFocus();

            btn_jump.setOnClickListener(view -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(scheme_uri.getText().toString()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                view.getContext().startActivity(intent);
                dismiss();
            });
        }, 100);
    }




    @Override
    protected void beforeDismiss() {
        super.beforeDismiss();
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.jump_scheme_uri_dialog;
    }
}