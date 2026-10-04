package io.local.questwebcinema;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Original vectors, with hints rendered inside the same Android/VR surface. */
public final class CinemaIcon extends View {
    public interface HintHost { void showHint(View anchor,String text); void hideHint(); }
    private final String icon;
    private final HintHost hints;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private boolean hover,selected,dark;
    private final Runnable showHint;
    public CinemaIcon(Context context,String icon,String description,boolean dark,HintHost hints,OnClickListener action){
        super(context);this.icon=icon;this.dark=dark;this.hints=hints;
        setContentDescription(description);setFocusable(true);setClickable(true);setOnClickListener(action);
        showHint=()->hints.showHint(this,description);setOnLongClickListener(v->{showHint.run();return true;});
        setOnFocusChangeListener((v,focus)->{if(focus)postDelayed(showHint,400);else{removeCallbacks(showHint);hints.hideHint();}invalidate();});
    }
    public void selected(boolean value){selected=value;invalidate();}
    public void dark(boolean value){dark=value;invalidate();}
    @Override public boolean onHoverEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_HOVER_ENTER){hover=true;postDelayed(showHint,450);invalidate();}if(e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT){hover=false;removeCallbacks(showHint);hints.hideHint();invalidate();}return super.onHoverEvent(e);}
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(showHint);super.onDetachedFromWindow();}
    private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,pen);}
    private void box(Canvas c,float x,float y,float w,float h,float r){c.drawRoundRect(new RectF(x,y,x+w,y+h),r,r,pen);}
    private void poly(Canvas c,float...p){path.reset();path.moveTo(p[0],p[1]);for(int i=2;i<p.length;i+=2)path.lineTo(p[i],p[i+1]);c.drawPath(path,pen);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);pen.setStyle(Paint.Style.FILL);pen.setColor(selected?0x22FB7299:(hover||isPressed()||hasFocus())?(dark?0xFF303139:0xFFF0F1F4):0);float d=getResources().getDisplayMetrics().density;float inset=d*4;c.drawRoundRect(new RectF(inset,inset,getWidth()-inset,getHeight()-inset),12,12,pen);c.save();float size=24*d;c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.75f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);pen.setColor(selected?0xFFFB7299:dark?0xFFD4D7DE:0xFF626977);
        switch(icon){
            case "home":poly(c,3,11,12,3,21,11);poly(c,5,10,5,21,10,21,10,15,14,15,14,21,19,21,19,10);break;
            case "search":c.drawCircle(10,10,6,pen);line(c,15,15,21,21);break;
            case "back":poly(c,14,5,7,12,14,19);break;
            case "forward":poly(c,10,5,17,12,10,19);break;
            case "refresh":c.drawArc(new RectF(4,4,20,20),35,285,false,pen);poly(c,21,4,21,10,15,10);break;
            case "bookmark":poly(c,6,21,6,4,18,4,18,21,12,17,6,21);break;
            case "history":c.drawCircle(12,12,9,pen);poly(c,12,6,12,12,17,15);break;
            case "live":box(c,2,6,15,13,3);poly(c,17,10,22,7,22,18,17,15);c.drawCircle(7,12,1.2f,pen);break;
            case "fire":poly(c,12,2,10,8,6,6,4,13,5,18,9,22,15,22,19,18,20,13,17,7,16,12,12,2);break;
            case "tv":box(c,2,7,20,14,3);poly(c,7,2,12,7,17,2);poly(c,10,11,15,14,10,17,10,11);break;
            case "film":box(c,3,3,18,18,2);line(c,7,3,7,21);line(c,17,3,17,21);for(int i=7;i<21;i+=5){line(c,3,i,7,i);line(c,17,i,21,i);}break;
            case "feed":line(c,4,5,20,5);line(c,4,12,13,12);line(c,4,19,20,19);c.drawCircle(19,12,1,pen);break;
            case "user":c.drawCircle(12,8,4,pen);c.drawArc(new RectF(4,14,20,28),180,180,false,pen);break;
            case "flat":box(c,2,4,20,14,2);line(c,12,18,12,22);line(c,8,22,16,22);break;
            case "curve":path.reset();path.moveTo(2,5);path.quadTo(12,9,22,5);path.lineTo(22,19);path.quadTo(12,23,2,19);path.close();c.drawPath(path,pen);break;
            case "fullscreen":poly(c,3,9,3,3,9,3);poly(c,15,3,21,3,21,9);poly(c,21,15,21,21,15,21);poly(c,9,21,3,21,3,15);break;
            case "restore":poly(c,3,9,9,9,9,3);poly(c,15,3,15,9,21,9);poly(c,21,15,15,15,15,21);poly(c,9,21,9,15,3,15);break;
            case "exit":poly(c,10,3,4,3,4,21,10,21);line(c,9,12,22,12);poly(c,17,7,22,12,17,17);break;
            case "close":line(c,6,6,18,18);line(c,18,6,6,18);break;
            case "theme":path.reset();path.moveTo(19,16);path.cubicTo(7,20,3,9,11,3);path.cubicTo(0,3,0,22,12,22);path.quadTo(19,22,21,15);c.drawPath(path,pen);break;
            case "settings":c.drawCircle(12,12,4,pen);c.drawCircle(12,12,8,pen);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);line(c,12,1,12,4);c.restore();}break;
            case "plus":line(c,5,12,19,12);line(c,12,5,12,19);break;
            case "minus":line(c,5,12,19,12);break;
            case "up":poly(c,5,15,12,8,19,15);break;
            case "down":poly(c,5,9,12,16,19,9);break;
            case "reset":c.drawCircle(12,12,5,pen);line(c,12,2,12,5);line(c,12,19,12,22);line(c,2,12,5,12);line(c,19,12,22,12);break;
            case "play":poly(c,8,4,20,12,8,20,8,4);break;
            case "volume":poly(c,3,9,7,9,12,4,12,20,7,15,3,15,3,9);c.drawArc(new RectF(7,3,22,21),-65,130,false,pen);break;
            case "clear":box(c,6,7,12,14,2);line(c,4,5,20,5);line(c,9,2,15,2);line(c,10,11,10,17);line(c,14,11,14,17);break;
            default:c.drawCircle(12,12,8,pen);line(c,12,7,12,13);c.drawCircle(12,17,0.5f,pen);
        }c.restore();
    }
}
