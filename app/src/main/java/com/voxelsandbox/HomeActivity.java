package com.voxelsandbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * 首页：标题 + 开始游戏 / 玩法说明 / 关于。
 * 点击「开始游戏」进入 {@link MainActivity} 的 3D 世界；按返回键回到首页。
 */
public class HomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_home);
        hideSystemUI();

        findViewById(R.id.btn_start).setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, MainActivity.class)));

        findViewById(R.id.btn_help).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("玩法说明")
                .setMessage("移动：按住屏幕左下角拖动虚拟摇杆\n"
                        + "视角：在屏幕其他区域滑动\n"
                        + "破坏：准星对准方块，点右下「破坏」\n"
                        + "放置：点右下「放置」（先在顶部物品栏选方块）\n"
                        + "跳跃：点「↑」；飞行：点右上「飞」，飞行时按住「↑」上升\n"
                        + "切换方块：点击顶部物品栏，方块名称会显示在下方")
                .setPositiveButton("知道了", null)
                .show())
        ;

        findViewById(R.id.btn_about).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("关于 Voxel Craft")
                .setMessage("一款原创的 3D 体素沙盒游戏。\n\n"
                        + "玩法灵感来自经典沙盒游戏，但代码、渲染器、地形算法与全部贴图资源均为原创，"
                        + "不使用任何第三方游戏引擎或素材。\n\n"
                        + "版本：v2.1.0")
                .setPositiveButton("好的", null)
                .show());
    }

    private void hideSystemUI() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }
}
