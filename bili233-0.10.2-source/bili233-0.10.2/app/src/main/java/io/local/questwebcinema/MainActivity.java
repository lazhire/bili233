package io.local.questwebcinema;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

public final class MainActivity extends Activity implements CinemaBrowserView.Host {
    private CinemaBrowserView browser;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);String url=getIntent().getStringExtra("url");if(state!=null)url=state.getString("url",url);browser=new CinemaBrowserView(this,this,false,url);setContentView(browser);if(getIntent().hasExtra("cinema_error"))browser.notice(getIntent().getStringExtra("cinema_error"));if(BuildConfig.DEBUG&&getIntent().getBooleanExtra("diagnostic",false))browser.requestDiagnosticSnapshot();if(BuildConfig.DEBUG&&getIntent().hasExtra("diagnostic_action"))browser.postDelayed(()->browser.diagnosticAction(getIntent().getStringExtra("diagnostic_action")),2500);}
    @Override public void mode(String mode){browser.pause();Intent i=new Intent(this,CinemaActivity.class);i.setAction(Intent.ACTION_MAIN);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);i.putExtra("url",browser.url());i.putExtra("mode",mode);if(BuildConfig.DEBUG)i.putExtra("diagnostic",getIntent().getBooleanExtra("diagnostic",false));startActivity(i);finishAndRemoveTask();}
    @Override public void adjust(String action){}
    @Override public boolean recenterIfNeeded(){return false;}
    @Override public void cursorVisible(boolean visible){}
    @Override public boolean movingWindow(){return false;}
    @Override public int curvature(){android.content.SharedPreferences p=getSharedPreferences("cinema",MODE_PRIVATE);return Math.max(0,Math.min(100,p.getInt("curvature",Math.round(p.getFloat("arc",90f)/1.2f))));}
    @Override public void curvature(int value){int n=Math.max(0,Math.min(100,value));getSharedPreferences("cinema",MODE_PRIVATE).edit().putInt("curvature",n).putFloat("arc",n*1.2f).apply();}
    @Override public void exit(){finish();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String url=intent.getStringExtra("url");if(Navigation.isWeb(url)&&!url.equals(browser.url()))browser.load(url);if(intent.hasExtra("cinema_error"))browser.notice(intent.getStringExtra("cinema_error"));if(BuildConfig.DEBUG&&intent.hasExtra("diagnostic_action"))browser.post(()->browser.diagnosticAction(intent.getStringExtra("diagnostic_action")));}
    @Override public void onBackPressed(){if(!browser.back())super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("url",browser.url());}
    @Override protected void onPause(){browser.pause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(browser!=null)browser.resume();}
    @Override protected void onDestroy(){browser.dispose();super.onDestroy();}
}
