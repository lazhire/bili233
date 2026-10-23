package io.local.questwebcinema;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;

/** Flat vector switch with a full-size controller target and accessible state. */
public final class FlatToggle extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean dark;
    private final CinemaIcon.HintHost hints;
    private final Runnable hint;
    private boolean checked;
    private float position;
    private ValueAnimator animator;
    public FlatToggle(Context context,String title,boolean initial,boolean dark,CinemaIcon.HintHost hints,Runnable action){
        super(context);this.dark=dark;this.hints=hints;checked=initial;position=initial?1:0;
        setContentDescription(title);setStateDescription(initial?"已开启":"已关闭");setFocusable(true);setClickable(true);
        hint=()->hints.showHint(this,title+(checked?"：已开启":"：已关闭"));
        setOnLongClickListener(v->{hint.run();return true;});
        setOnFocusChangeListener((v,focus)->{removeCallbacks(hint);if(focus)postDelayed(hint,450);else hints.hideHint();invalidate();});
        setOnClickListener(v->{checked=!checked;setStateDescription(checked?"已开启":"已关闭");if(animator!=null)animator.cancel();
            float target=checked?1:0;if(ValueAnimator.areAnimatorsEnabled()){animator=ValueAnimator.ofFloat(position,target);animator.setDuration(170);animator.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});animator.start();}else{position=target;invalidate();}
            hints.hideHint();action.run();});
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Switch");info.setCheckable(true);info.setChecked(checked);}
    @Override public boolean onHoverEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_HOVER_ENTER)postDelayed(hint,450);if(e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT){removeCallbacks(hint);hints.hideHint();}return super.onHoverEvent(e);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density,cx=getWidth()/2f,cy=getHeight()/2f;
        int off=dark?0xff444750:0xffc8cbd2,on=0xfffb7299;paint.setColor((int)new android.animation.ArgbEvaluator().evaluate(position,off,on));paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(cx-25*d,cy-15*d,cx+25*d,cy+15*d,15*d,15*d,paint);paint.setColor(Color.WHITE);c.drawCircle(cx+(position*2-1)*10*d,cy,12*d,paint);
        if(hasFocus()){paint.setColor(on);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*d);c.drawRoundRect(cx-29*d,cy-19*d,cx+29*d,cy+19*d,19*d,19*d,paint);}
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(hint);if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
