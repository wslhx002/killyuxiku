package com.voxelsandbox;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 入口 Activity：全屏 GLSurfaceView + 自绘 HUD（准星/摇杆/按钮/物品栏），
 * 所有触摸在 onTouchEvent 统一分发。
 */
public class MainActivity extends Activity {

    private GLSurfaceView glView;
    private InputState input;
    private GameRenderer renderer;
    private OverlayView overlay;

    private int screenW, screenH;

    // 触摸角色
    private static final int R_LOOK = 0, R_MOVE = 1, R_BREAK = 2, R_PLACE = 3,
            R_JUMP = 4, R_FLY = 5, R_SLOT = 6;

    private static class Ptr {
        int role;
        float startX, startY, lastX, lastY;
        int index; // 按钮/slot 索引
    }
    private final SparseArray<Ptr> pointers = new SparseArray<>();

    // UI 区域（运行时按屏幕计算）
    private final Rect jumpBtn = new Rect();
    private final Rect placeBtn = new Rect();
    private final Rect breakBtn = new Rect();
    private final Rect flyBtn = new Rect();
    private final Rect slotBar = new Rect();
    private final Rect[] slots = new Rect[Block.PALETTE.length];

    private float joyDx, joyDy; // 摇杆当前偏移（用于绘制）
    private boolean joyActive;
    private int pressedBtn = -1; // 视觉按下的按钮

    private static final float SENS = 0.0055f;
    private static final String VERSION_TAG = "v2.0.0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashHandler();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        input = new InputState();
        renderer = new GameRenderer(input);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        // 不强制 RGBA8888：使用系统默认配置（RGB565+depth16），在模拟器与各类 GPU 上兼容性最好，
        // 避免因找不到匹配 EGL 配置而抛 IllegalArgumentException 导致打开即闪退。
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glView.setPreserveEGLContextOnPause(true);

        overlay = new OverlayView(this);
        overlay.setWillNotDraw(false);
        // 每帧重绘 HUD（昼夜时钟需要持续刷新）
        renderer.setOnFrame(() -> overlay.postInvalidate());

        FrameLayout root = new FrameLayout(this);
        root.addView(glView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    /** 全局崩溃捕获：把堆栈写入 logcat 与私有目录 crash.txt，便于定位闪退原因 */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                String stack = sw.toString();
                Log.e("VoxelCrash", "Uncaught in " + thread.getName() + "\n" + stack);
                java.io.File dir = getExternalFilesDir(null);
                if (dir == null) dir = getFilesDir();
                FileWriter fw = new FileWriter(new File(dir, "crash.txt"), false);
                fw.write(stack);
                fw.close();
            } catch (Throwable ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    private void computeLayout() {
        screenW = overlay.getWidth();
        screenH = overlay.getHeight();
        if (screenW == 0) return;
        int bs = dp(70);
        int margin = dp(20);
        breakBtn.set(screenW - margin - bs, screenH - margin - bs, screenW - margin, screenH - margin);
        placeBtn.set(breakBtn.left - bs - dp(12), breakBtn.top, breakBtn.left - dp(12), breakBtn.bottom);
        jumpBtn.set(breakBtn.left, breakBtn.top - bs - dp(12), breakBtn.right, breakBtn.top - dp(12));
        flyBtn.set(screenW - margin - bs, margin, screenW - margin, margin + bs);

        int slotSize = dp(42);
        int gap = dp(4);
        int total = slots.length * slotSize + (slots.length - 1) * gap;
        int left = (screenW - total) / 2;
        int top = margin;
        slotBar.set(left - dp(6), top - dp(6), left + total + dp(6), top + slotSize + dp(6));
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Rect(left + i * (slotSize + gap), top,
                    left + i * (slotSize + gap) + slotSize, top + slotSize);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
        computeLayout();
    }

    private void hideSystemUI() {
        View decor = getWindow().getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decor.setSystemUiVisibility(flags);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (screenW == 0) computeLayout();
        int action = event.getActionMasked();
        int idx = event.getActionIndex();
        int pid = event.getPointerId(idx);
        float x = event.getX(idx);
        float y = event.getY(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                Ptr p = new Ptr();
                p.startX = p.lastX = x;
                p.startY = p.lastY = y;
                p.role = roleFor(x, y, p);
                pointers.put(pid, p);
                if (p.role == R_MOVE) { joyActive = true; }
                if (p.role >= R_BREAK && p.role <= R_FLY) pressedBtn = p.role;
                overlay.invalidate();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    Ptr p = pointers.get(id);
                    if (p == null) continue;
                    float mx = event.getX(i), my = event.getY(i);
                    switch (p.role) {
                        case R_LOOK:
                            input.addLook(-(mx - p.lastX) * SENS, -(my - p.lastY) * SENS);
                            break;
                        case R_MOVE:
                            joyDx = mx - p.startX;
                            joyDy = my - p.startY;
                            float r = dp(55);
                            float len = (float) Math.sqrt(joyDx * joyDx + joyDy * joyDy);
                            if (len > r) { joyDx = joyDx / len * r; joyDy = joyDy / len * r; }
                            input.setMove(clamp(joyDx / r), clamp(-joyDy / r));
                            break;
                    }
                    p.lastX = mx; p.lastY = my;
                }
                overlay.invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                Ptr p = pointers.get(pid);
                if (p != null) {
                    float dx = x - p.startX, dy = y - p.startY;
                    boolean tapped = dx * dx + dy * dy < dp(30) * dp(30);
                    switch (p.role) {
                        case R_MOVE:
                            input.setMove(0, 0);
                            joyActive = false; joyDx = 0; joyDy = 0;
                            break;
                        case R_BREAK: if (tapped) input.queueBreak(); break;
                        case R_PLACE: if (tapped) input.queuePlace(); break;
                        case R_JUMP: if (tapped) input.queueJump(); break;
                        case R_FLY: if (tapped) input.queueFlyToggle(); break;
                        case R_SLOT: if (tapped) input.selectedSlot = p.index; break;
                    }
                    pressedBtn = -1;
                    pointers.remove(pid);
                }
                overlay.invalidate();
                break;
            }
        }
        return true;
    }

    private int roleFor(float x, float y, Ptr p) {
        if (jumpBtn.contains((int) x, (int) y)) return R_JUMP;
        if (breakBtn.contains((int) x, (int) y)) return R_BREAK;
        if (placeBtn.contains((int) x, (int) y)) return R_PLACE;
        if (flyBtn.contains((int) x, (int) y)) return R_FLY;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && slots[i].contains((int) x, (int) y)) { p.index = i; return R_SLOT; }
        }
        // 左下区域 → 摇杆
        if (x < screenW * 0.45f && y > screenH * 0.40f) return R_MOVE;
        return R_LOOK;
    }

    private static float clamp(float v) { return v < -1 ? -1 : (v > 1 ? 1 : v); }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glView.onPause();
    }

    // ---- 自绘 HUD ----
    private class OverlayView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public OverlayView(Activity a) { super(a); }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            computeLayout();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (screenW == 0) computeLayout();
            drawCrosshair(canvas);
            drawSlotBar(canvas);
            drawFlyButton(canvas);
            drawActionButtons(canvas);
            drawJoystick(canvas);
            drawClock(canvas);
            drawDebug(canvas);
        }

        private void drawDebug(Canvas c) {
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(false);
            paint.setColor(0x99FFFFFF);
            paint.setTextSize(dp(11));
            c.drawText(VERSION_TAG, dp(12), screenH - dp(10), paint);

            String err = renderer != null ? renderer.initError : null;
            if (err != null) {
                paint.setColor(0xFFFF5252);
                paint.setTextSize(dp(12));
                float y = screenH / 2f + dp(40);
                int n = 60;
                for (int i = 0; i < err.length(); i += n) {
                    c.drawText(err.substring(i, Math.min(err.length(), i + n)), dp(20), y, paint);
                    y += dp(16);
                }
            }
        }

        private void drawClock(Canvas c) {
            if (renderer == null) return;
            float t = renderer.dayTime();
            int minutes = (int) (t * 24 * 60);
            int hh = (minutes / 60) % 24;
            int mm = minutes % 60;
            boolean day = renderer.isDay();
            String label = (day ? "☀ " : "🌙 ") + String.format("%02d:%02d", hh, mm);

            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(dp(15));
            float x = dp(16), y = dp(40);
            float w = paint.measureText(label);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(day ? 0x66000000 : 0x55000000);
            c.drawRoundRect(new RectF(x - dp(8), y - dp(18), x + w + dp(8), y + dp(6)),
                    dp(8), dp(8), paint);
            paint.setColor(day ? 0xFFFFFFFF : 0xFFD6DCFF);
            paint.setFakeBoldText(true);
            c.drawText(label, x, y, paint);
            paint.setFakeBoldText(false);
        }

        private void drawCrosshair(Canvas c) {
            paint.setColor(0xCCFFFFFF);
            paint.setStrokeWidth(dp(2));
            float cx = screenW / 2f, cy = screenH / 2f, s = dp(9);
            c.drawLine(cx - s, cy, cx + s, cy, paint);
            c.drawLine(cx, cy - s, cx, cy + s, paint);
            paint.setColor(0x55000000);
            paint.setStrokeWidth(dp(1));
            c.drawCircle(cx, cy, dp(11), paint);
        }

        private void drawSlotBar(Canvas c) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x66000000);
            c.drawRoundRect(new RectF(slotBar), dp(8), dp(8), paint);
            for (int i = 0; i < slots.length; i++) {
                Rect r = slots[i];
                if (r == null) continue;
                paint.setColor(previewColor(Block.PALETTE[i]));
                c.drawRoundRect(new RectF(r), dp(4), dp(4), paint);
                if (i == input.selectedSlot) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(0xFFFFFFFF);
                    paint.setStrokeWidth(dp(3));
                    c.drawRoundRect(new RectF(r), dp(4), dp(4), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
            // 当前方块名
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(12));
            paint.setColor(0xE6FFFFFF);
            paint.setFakeBoldText(true);
            c.drawText(blockName(Block.PALETTE[input.selectedSlot]),
                    (slotBar.left + slotBar.right) / 2f, slotBar.bottom + dp(15), paint);
            paint.setFakeBoldText(false);
        }

        private void drawFlyButton(Canvas c) {
            drawCircleButton(c, flyBtn, input.flying ? 0xFF4FC3F7 : 0x884FC3F7,
                    pressedBtn == R_FLY, "F");
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(16));
            c.drawText("飞", flyBtn.exactCenterX(), flyBtn.exactCenterY() + dp(6), paint);
        }

        private void drawActionButtons(Canvas c) {
            drawCircleButton(c, jumpBtn, 0x8866BB6A, pressedBtn == R_JUMP, "");
            drawCircleButton(c, breakBtn, 0x88EF5350, pressedBtn == R_BREAK, "");
            drawCircleButton(c, placeBtn, 0x8842A5F5, pressedBtn == R_PLACE, "");

            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(22));
            paint.setFakeBoldText(true);
            c.drawText("↑", jumpBtn.exactCenterX(), jumpBtn.exactCenterY() + dp(8), paint);
            c.drawText("⛏", breakBtn.exactCenterX(), breakBtn.exactCenterY() + dp(8), paint);
            c.drawText("+", placeBtn.exactCenterX(), placeBtn.exactCenterY() + dp(8), paint);
            paint.setFakeBoldText(false);
            paint.setTextSize(dp(11));
            paint.setColor(0xCCFFFFFF);
            c.drawText("破坏", breakBtn.exactCenterX(), breakBtn.bottom + dp(14), paint);
            c.drawText("放置", placeBtn.exactCenterX(), placeBtn.bottom + dp(14), paint);
        }

        private void drawCircleButton(Canvas c, Rect r, int color, boolean pressed, String tag) {
            float cx = r.exactCenterX(), cy = r.exactCenterY(), rad = r.width() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? 0xCCFFFFFF : color);
            c.drawCircle(cx, cy, rad, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xAAFFFFFF);
            paint.setStrokeWidth(dp(2));
            c.drawCircle(cx, cy, rad, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawJoystick(Canvas c) {
            float cx = dp(100), cy = screenH - dp(100);
            float baseR = dp(55), knobR = dp(24);
            // 静止时也显示淡淡的底盘，提示摇杆位置
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(joyActive ? 0x33FFFFFF : 0x1FFFFFFF);
            c.drawCircle(cx, cy, baseR, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(joyActive ? 0x77FFFFFF : 0x33FFFFFF);
            paint.setStrokeWidth(dp(2));
            c.drawCircle(cx, cy, baseR, paint);
            if (!joyActive) return;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCCFFFFFF);
            c.drawCircle(cx + joyDx, cy + joyDy, knobR, paint);
        }
    }

    private static int previewColor(int id) {
        switch (id) {
            case Block.GRASS:  return 0xFF5fae3a;
            case Block.DIRT:   return 0xFF8a5a2b;
            case Block.STONE:  return 0xFF8a8a8a;
            case Block.WOOD:   return 0xFF875a32;
            case Block.LEAVES: return 0xFF2f7d31;
            case Block.SAND:   return 0xFFe6d79a;
            case Block.PLANK:  return 0xFFb5864a;
            case Block.COBBLE: return 0xFF7d7d7d;
            case Block.BRICK:  return 0xFFa14a3a;
            case Block.SNOW:   return 0xFFf0f4f8;
            case Block.GLASS:  return 0xFFb0bec5;
            case Block.GLOW:   return 0xFFd9a441;
            default:           return 0xFF888888;
        }
    }

    private static String blockName(int id) {
        switch (id) {
            case Block.GRASS:  return "草地";
            case Block.DIRT:   return "泥土";
            case Block.STONE:  return "石头";
            case Block.WOOD:   return "木头";
            case Block.LEAVES: return "树叶";
            case Block.SAND:   return "沙子";
            case Block.PLANK:  return "木板";
            case Block.COBBLE: return "圆石";
            case Block.BRICK:  return "红砖";
            case Block.SNOW:   return "积雪";
            case Block.GLASS:  return "玻璃";
            case Block.GLOW:   return "发光石";
            default:           return "";
        }
    }
}
