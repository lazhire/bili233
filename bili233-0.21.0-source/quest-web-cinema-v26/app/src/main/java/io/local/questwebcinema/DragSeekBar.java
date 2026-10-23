package io.local.questwebcinema;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.SeekBar;

/** Immediate track dragging with one pointer and no parent scroll interception. */
public final class DragSeekBar extends SeekBar {
    private OnSeekBarChangeListener listener;
    private int activePointer=-1;
    private boolean settingTouchValue,cancelled;
    public boolean wasCancelled(){return cancelled;}
    public DragSeekBar(Context context){super(context);setSplitTrack(false);}
    @Override public void setOnSeekBarChangeListener(OnSeekBarChangeListener value){
        listener=value;
        super.setOnSeekBarChangeListener(value==null?null:new OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){if(!settingTouchValue&&listener!=null)listener.onProgressChanged(bar,progress,user);}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
    }
    private void update(MotionEvent event,int index){
        float span=getWidth()-getPaddingLeft()-getPaddingRight();if(span<=0)return;
        float fraction=(event.getX(index)-getPaddingLeft())/span;
        if(getLayoutDirection()==LAYOUT_DIRECTION_RTL)fraction=1-fraction;
        int value=Math.round(Math.max(0f,Math.min(1f,fraction))*getMax());
        if(value==getProgress())return;
        settingTouchValue=true;setProgress(value);settingTouchValue=false;
        if(listener!=null)listener.onProgressChanged(this,value,true);
    }
    private void finish(boolean cancel){
        cancelled=cancel;
        if(activePointer==-1)return;activePointer=-1;setPressed(false);
        ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);
        if(listener!=null)listener.onStopTrackingTouch(this);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled())return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            cancelled=false;activePointer=event.getPointerId(0);setPressed(true);requestFocus();
            ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(true);
            if(listener!=null)listener.onStartTrackingTouch(this);update(event,0);return true;
        }
        if(activePointer==-1)return false;
        int index=event.findPointerIndex(activePointer);
        if(action==MotionEvent.ACTION_CANCEL||index<0){finish(true);return true;}
        if(action==MotionEvent.ACTION_MOVE){update(event,index);return true;}
        if(action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==activePointer)){
            update(event,index);finish(false);return true;
        }
        return true;
    }
    @Override protected void onDetachedFromWindow(){finish(true);super.onDetachedFromWindow();}
}
