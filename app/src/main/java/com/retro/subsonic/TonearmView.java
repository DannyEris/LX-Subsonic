package com.retro.subsonic;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class TonearmView extends View {

    // 旋转基准角度：暂停时外摆 -32度，播放时摆入黑胶唱片唱轨处 -3度
    private static final float ANGLE_PAUSED = -32.0f;
    private static final float ANGLE_PLAYING = -3.0f;

    private float currentAngle = ANGLE_PAUSED;
    private boolean isPlaying = false;
    private ValueAnimator animator;

    private Paint basePaint;
    private Paint armPaint;
    private Paint headPaint;
    private Paint needlePaint;
    private Path armPath;

    public TonearmView(Context context) {
        super(context);
        init();
    }

    public TonearmView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TonearmView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setStyle(Paint.Style.FILL);

        armPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        armPaint.setStyle(Paint.Style.STROKE);
        armPaint.setStrokeCap(Paint.Cap.ROUND);

        headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headPaint.setStyle(Paint.Style.FILL);

        needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        needlePaint.setStyle(Paint.Style.FILL);

        armPath = new Path();
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        float targetAngle = playing ? ANGLE_PLAYING : ANGLE_PAUSED;
        animator = ValueAnimator.ofFloat(currentAngle, targetAngle);
        animator.setDuration(450);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                currentAngle = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 唱臂基座固定在右上角区域
        float pivotX = w * 0.82f;
        float pivotY = h * 0.16f;

        canvas.save();
        // 绕基座轴心旋转
        canvas.rotate(currentAngle, pivotX, pivotY);

        float density = getResources().getDisplayMetrics().density;

        // 1. 唱臂金属杆（复古S型微弧金属杆身）
        armPaint.setStrokeWidth(3.5f * density);
        armPaint.setShader(new LinearGradient(pivotX, pivotY, pivotX - w * 0.5f, pivotY + h * 0.6f,
                new int[]{0xFFE0E0E0, 0xFF888888, 0xFF00E5FF}, null, Shader.TileMode.CLAMP));

        armPath.reset();
        armPath.moveTo(pivotX, pivotY);
        float endArmX = pivotX - w * 0.44f;
        float endArmY = pivotY + h * 0.62f;
        armPath.quadTo(pivotX - w * 0.15f, pivotY + h * 0.35f, endArmX, endArmY);
        canvas.drawPath(armPath, armPaint);
        armPaint.setShader(null);

        // 2. 唱头盒 (Cartridge)
        headPaint.setColor(0xFF222530);
        canvas.save();
        canvas.translate(endArmX, endArmY);
        canvas.rotate(18); // 唱头顺应角度
        canvas.drawRoundRect(-7 * density, 0, 7 * density, 22 * density, 3 * density, 3 * density, headPaint);

        // 唱头高光边
        headPaint.setColor(0xFF00E5FF);
        canvas.drawCircle(0, 5 * density, 2 * density, headPaint);

        // 唱针针尖 (Stylus)
        needlePaint.setColor(0xFFFF5252);
        canvas.drawRect(-1.5f * density, 20 * density, 1.5f * density, 26 * density, needlePaint);
        canvas.restore();

        // 3. 顶部配重陀 (Counterweight)
        basePaint.setColor(0xFF424242);
        canvas.drawCircle(pivotX + 8 * density, pivotY - 12 * density, 8 * density, basePaint);

        canvas.restore();

        // 4. 旋转底座（不跟随旋转，稳定在机身上）
        basePaint.setColor(0xFF14171F);
        canvas.drawCircle(pivotX, pivotY, 15 * density, basePaint);
        basePaint.setColor(0xFF2C3240);
        canvas.drawCircle(pivotX, pivotY, 11 * density, basePaint);
        basePaint.setColor(0xFF00E5FF);
        canvas.drawCircle(pivotX, pivotY, 4 * density, basePaint);
    }
}
