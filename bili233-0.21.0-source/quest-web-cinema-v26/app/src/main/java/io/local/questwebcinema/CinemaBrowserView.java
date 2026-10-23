package io.local.questwebcinema;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Message;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Desktop-inspired icon chrome, shared by the window and the spatial display. */
public final class CinemaBrowserView extends FrameLayout implements CinemaIcon.HintHost {
    public interface Host { void mode(String mode); void adjust(String action); int curvature(); void curvature(int value); boolean recenterIfNeeded(); boolean movingWindow(); void cursorVisible(boolean visible); void exit(); }
    private final Host host;
    private final boolean immersive;
    private final SharedPreferences prefs;
    private final LinearLayout sidebar,header,compact,content,loading;
    private final FrameLayout stage;
    private final EditText address;
    private final TextView status,tooltip;
    private TextView geometryLabel,curveValue;
    private SeekBar curvatureControl;
    private boolean curveDragging;
    private final ProgressBar progress;
    private final List<CinemaIcon> icons=new ArrayList<>();
    private final Set<WebView> popups=new HashSet<>();
    private final List<JSONObject> sidebarItems=new ArrayList<>();
    private LinearLayout sideNav,tabsRow;
    private CinemaIcon compactMute;
    private View tabStrip;
    private static final class BrowserTab { WebView view; androidx.webkit.ScriptHandler themeStart; boolean muted; String pageState; String url,title="新标签页"; BrowserTab(String url){this.url=url;} }
    private final List<BrowserTab> tabs=new ArrayList<>();
    private BrowserTab activeTab;
    private WebView web;
    private String mobileAgent,pendingUrl,modeText="";
    private View fullscreenView,overlay;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private boolean focus,disposed,paused,starting,diagnostic,dark;
    private int bg,railColor,text,muted,field;
    private final int pink=0xFFFB7299;
    private final Runnable loadTimeout=this::loadingSlow;
    private String layoutScript,homeScript;
    private int pageGeneration;
    private boolean pageLoadTimedOut,pageFinished,pagePublished,pageError,pointerVisible,pointerPressed;
    private float pointerX,pointerY,activityPointerX,activityPointerY;
    private long pointerActivity=android.os.SystemClock.uptimeMillis();
    private boolean videoPlaying;
    private final Runnable playerPoll=this::pollPlayer;
    private void pollPlayer(){if(disposed||paused)return;final WebView owner=web;if(owner!=null)owner.evaluateJavascript("Boolean(window.__questBrowser&&window.__questBrowser.playing())",value->{if(disposed||owner!=web)return;videoPlaying="true".equals(value);updatePointerVisibility();});postDelayed(playerPoll,700);}
    private void updatePointerVisibility(){boolean hidden=immersive&&videoPlaying&&overlay==null&&!pointerPressed&&android.os.SystemClock.uptimeMillis()-pointerActivity>2500;if(hidden)pointerVisible=false;host.cursorVisible(!hidden);invalidate();}

    private final Paint pointerPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable pageMonitor=this::monitorPage;
    private String lastNotice="";
    private long lastNoticeAt;
    private final Runnable hideNotice=this::hideStatus;
    private void hideStatus(){status.setVisibility(GONE);}

    public CinemaBrowserView(Context context,Host host,boolean immersive,String initialUrl){
        super(context);this.host=host;this.immersive=immersive;
        prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE);
        dark=prefs.getBoolean("dark",immersive);palette();pendingUrl=Navigation.isWeb(initialUrl)?initialUrl:Navigation.HOME;setBackgroundColor(bg);
        LinearLayout shell=row();addView(shell,new FrameLayout.LayoutParams(-1,-1));
        sidebar=column();sidebar.setPadding(dp(10),dp(12),dp(10),dp(12));sidebar.setBackgroundColor(railColor);shell.addView(sidebar,new LinearLayout.LayoutParams(dp(76),-1));
        sideNav=column();sideNav.setGravity(Gravity.TOP);ScrollView railScroll=new ScrollView(context);railScroll.setVerticalScrollBarEnabled(false);railScroll.addView(sideNav);sidebar.addView(railScroll,new LinearLayout.LayoutParams(-1,0,1));
        loadSidebar();rebuildSidebar();sidebar.addView(icon("settings","设置",v->showSettings()));
        content=column();shell.addView(content,new LinearLayout.LayoutParams(0,-1,1));
        HorizontalScrollView tabScroll=new HorizontalScrollView(context);tabScroll.setHorizontalScrollBarEnabled(false);tabScroll.setBackgroundColor(railColor);tabsRow=row();tabScroll.addView(tabsRow);tabStrip=tabScroll;content.addView(tabScroll,new LinearLayout.LayoutParams(-1,dp(52)));activeTab=new BrowserTab(pendingUrl);activeTab.muted=context instanceof android.app.Activity&&((android.app.Activity)context).getIntent().getBooleanExtra("tab_muted",false);tabs.add(activeTab);rebuildTabs();
        header=row();header.setPadding(dp(8),dp(4),dp(8),dp(4));header.setBackgroundColor(railColor);content.addView(header,new LinearLayout.LayoutParams(-1,dp(60)));
        header.addView(icon("back","后退",v->webBack()));header.addView(icon("forward","前进",v->forward()));header.addView(icon("refresh","刷新",v->reload()));header.addView(icon("home","主页",v->load(Navigation.HOME)));
        address=new EditText(context);address.setSingleLine(true);address.setTextSize(14);address.setTextColor(text);address.setHintTextColor(muted);address.setHint("搜索或输入网址");address.setSelectAllOnFocus(true);address.setBackground(round(field,18));address.setPadding(dp(16),0,dp(12),0);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(EditorInfo.IME_ACTION_GO);address.setOnEditorActionListener((v,a,e)->{navigate();return true;});header.addView(address,new LinearLayout.LayoutParams(0,dp(42),1));
        header.addView(icon("search","打开网址 / 搜索",v->navigate()));
        compact=row();compact.setPadding(dp(8),0,dp(8),0);compact.setBackgroundColor(railColor);compact.addView(icon("back","网页后退",v->webBack()));compact.addView(icon("forward","网页前进",v->forward()));compact.addView(icon("volume","播放与音量",v->showPlayerControls()));compactMute=icon("volume","静音当前网页",v->toggleTabMute(activeTab));compact.addView(compactMute);updateMuteIcon();compact.addView(new View(context),new LinearLayout.LayoutParams(0,1,1));compact.addView(icon("fullscreen","退出全屏",v->leaveFocus()));content.addView(compact,new LinearLayout.LayoutParams(-1,dp(56)));compact.setVisibility(GONE);
        progress=new ProgressBar(context);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(muted));FrameLayout.LayoutParams progressPosition=new FrameLayout.LayoutParams(dp(16),dp(16),Gravity.BOTTOM|Gravity.LEFT);progressPosition.leftMargin=dp(84);progressPosition.bottomMargin=dp(24);addView(progress,progressPosition);progress.setVisibility(GONE);
        stage=new FrameLayout(context);stage.setBackground(round(bg,14));stage.setClipToOutline(true);LinearLayout.LayoutParams stageLayout=new LinearLayout.LayoutParams(-1,0,1);stageLayout.setMargins(dp(8),dp(6),dp(10),dp(10));content.addView(stage,stageLayout);status=label("",12,muted);status.setPadding(dp(12),dp(8),dp(12),dp(8));status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setBackground(round(railColor,8));status.setVisibility(GONE);FrameLayout.LayoutParams statusPosition=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);statusPosition.leftMargin=dp(106);statusPosition.bottomMargin=dp(12);statusPosition.rightMargin=dp(18);addView(status,statusPosition);
        loading=column();loading.setGravity(Gravity.BOTTOM|Gravity.LEFT);loading.setPadding(dp(16),dp(16),dp(16),dp(20));loading.setBackgroundColor(bg);stage.addView(loading,new FrameLayout.LayoutParams(-1,-1));loading.setVisibility(GONE);
        tooltip=label("",14,Color.WHITE);tooltip.setPadding(dp(13),dp(9),dp(13),dp(9));tooltip.setBackground(round(0xEE24262D,8));tooltip.setElevation(dp(20));tooltip.setVisibility(GONE);tooltip.setClickable(false);addView(tooltip,new FrameLayout.LayoutParams(-2,-2));if(!immersive)post(this::startBrowsing);if(immersive)post(playerPoll);
    }

    private void forgetIcons(View root){icons.removeIf(i->{ViewParent parent=i.getParent();while(parent instanceof View){if(parent==root)return true;parent=parent.getParent();}return false;});}
    private JSONObject sidebarItem(String name,String glyph,String action,String url){JSONObject o=new JSONObject();try{o.put("name",name).put("icon",glyph).put("action",action).put("url",url).put("enabled",true);}catch(JSONException ignored){}return o;}
    private void defaultSidebar(){sidebarItems.clear();sidebarItems.add(sidebarItem("主页","home","link",Navigation.HOME));sidebarItems.add(sidebarItem("热门","fire","link","https://www.bilibili.com/v/popular/all/"));sidebarItems.add(sidebarItem("排行榜","tv","link","https://www.bilibili.com/v/popular/rank/all/"));sidebarItems.add(sidebarItem("动态","feed","link","https://t.bilibili.com/"));sidebarItems.add(sidebarItem("影院调节","curve","cinema",""));sidebarItems.add(sidebarItem("明暗主题","theme","theme",""));}
    private void loadSidebar(){try{String saved=prefs.getString("sidebarButtons",null);if(saved==null){defaultSidebar();return;}JSONArray a=new JSONArray(saved);for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);String action=item.optString("action","link");if(!"history".equals(action)&&!"bookmarks".equals(action)&&!"exit".equals(action))sidebarItems.add(item);}prefs.edit().putString("sidebarButtons",new JSONArray(sidebarItems).toString()).apply();}catch(JSONException e){defaultSidebar();}}
    private void saveSidebar(){prefs.edit().putString("sidebarButtons",new JSONArray(sidebarItems).toString()).apply();rebuildSidebar();}
    private void rebuildSidebar(){forgetIcons(sideNav);sideNav.removeAllViews();String previousGroup="";for(JSONObject o:sidebarItems)if(o.optBoolean("enabled",true)){
        String action=o.optString("action","link"),group="link".equals(action)?"browse":("history".equals(action)||"bookmarks".equals(action)?"library":"tools");
        if(!previousGroup.isEmpty()&&!previousGroup.equals(group)){View divider=new View(getContext());divider.setBackgroundColor(dark?0xFF34343C:0xFFE0E0E6);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(dp(28),dp(1));line.gravity=Gravity.CENTER_HORIZONTAL;line.setMargins(0,dp(8),0,dp(8));sideNav.addView(divider,line);}previousGroup=group;
        CinemaIcon button=icon(o.optString("icon","bookmark"),("cinema".equals(action)?(immersive?"影院调节":"进入沉浸模式"):o.optString("name","快捷入口")),v->{switch(action){case "history":showLibrary("历史","history");break;case "bookmarks":showLibrary("收藏","bookmarks");break;case "cinema":openCinema();break;case "theme":toggleTheme();break;case "exit":host.exit();break;default:load(o.optString("url",Navigation.HOME));}});button.setTag(o);sideNav.addView(button);
    }if(!immersive&&sidebarItems.stream().noneMatch(o->"cinema".equals(o.optString("action"))&&o.optBoolean("enabled",true)))sideNav.addView(icon("curve","进入沉浸模式",v->openCinema()));if(immersive)sideNav.addView(icon("exit","退出沉浸模式",v->host.exit()));updateSidebarSelection();}
    private void showSidebarEditor(){LinearLayout p=sheet("自定义侧栏");for(int i=0;i<sidebarItems.size();i++){final int index=i;JSONObject o=sidebarItems.get(i);LinearLayout r=row();CheckBox enabled=new CheckBox(getContext());enabled.setText(o.optString("name"));enabled.setTextColor(text);enabled.setButtonTintList(android.content.res.ColorStateList.valueOf(dark?0xFFFB92AF:pink));enabled.setChecked(o.optBoolean("enabled",true));enabled.setOnCheckedChangeListener((b,value)->{try{o.put("enabled",value);}catch(JSONException ignored){}saveSidebar();});r.addView(enabled,new LinearLayout.LayoutParams(0,dp(58),1));r.addView(icon("up","上移",v->{if(index>0){Collections.swap(sidebarItems,index,index-1);saveSidebar();showSidebarEditor();}}));r.addView(icon("down","下移",v->{if(index+1<sidebarItems.size()){Collections.swap(sidebarItems,index,index+1);saveSidebar();showSidebarEditor();}}));r.addView(icon("settings","编辑名称、图标和链接",v->editSidebar(o)));r.addView(icon("clear","移除此按钮",v->{sidebarItems.remove(o);saveSidebar();showSidebarEditor();}));p.addView(r);}LinearLayout actions=row();actions.addView(icon("plus","添加快捷链接",v->editSidebar(null)));actions.addView(icon("reset","恢复默认侧栏",v->{defaultSidebar();saveSidebar();showSidebarEditor();}));p.addView(actions);}
    private EditText editorField(String value,String hint){EditText e=new EditText(getContext());e.setText(value);e.setHint(hint);e.setTextColor(text);e.setHintTextColor(muted);e.setSingleLine();e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(muted));return e;}
    private void editSidebar(JSONObject item){LinearLayout p=sheet(item==null?"添加快捷链接":"编辑侧栏按钮");EditText name=editorField(item==null?"":item.optString("name"),"悬停显示的名称");p.addView(name);String action=item==null?"link":item.optString("action","link");EditText link=editorField(item==null?"https://":item.optString("url"),"HTTPS 网址");if("link".equals(action))p.addView(link);String[] glyphs={"home","bookmark","search","fire","feed","live","tv","film","user","history","curve","theme","settings","play","exit"};String[] glyphNames={"主页","收藏","搜索","热门","动态","直播","电视","电影","用户","历史","曲面影院","明暗主题","设置","播放","退出"};Spinner spinner=new Spinner(getContext());ArrayAdapter<String> adapter=new ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_dropdown_item,glyphNames){private View themed(View view){if(view instanceof TextView){((TextView)view).setTextColor(text);((TextView)view).setPadding(dp(16),dp(12),dp(16),dp(12));}view.setBackgroundColor(field);return view;}@Override public View getView(int position,View convert,ViewGroup parent){return themed(super.getView(position,convert,parent));}@Override public View getDropDownView(int position,View convert,ViewGroup parent){return themed(super.getDropDownView(position,convert,parent));}};spinner.setPopupBackgroundDrawable(round(field,12));spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(muted));spinner.setAdapter(adapter);for(int i=0;i<glyphs.length;i++)if(glyphs[i].equals(item==null?"bookmark":item.optString("icon")))spinner.setSelection(i);p.addView(spinner);Button save=new Button(getContext());save.setText("保存");save.setTextColor(text);save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(field));save.setOnClickListener(v->{String n=name.getText().toString().trim(),u=link.getText().toString().trim();if(n.isEmpty()){notice("请填写名称");return;}if("link".equals(action)&&!Navigation.isWeb(u)){notice("请填写完整 HTTPS 网址");return;}JSONObject o=item==null?sidebarItem(n,glyphs[spinner.getSelectedItemPosition()],action,u):item;try{o.put("name",n).put("icon",glyphs[spinner.getSelectedItemPosition()]).put("url",u);}catch(JSONException ignored){}if(item==null)sidebarItems.add(o);saveSidebar();showSidebarEditor();});p.addView(save);}
    private void rebuildTabs(){
        forgetIcons(tabsRow);tabsRow.removeAllViews();tabStrip.setBackgroundColor(railColor);
        tabsRow.setPadding(dp(6),dp(4),dp(6),0);
        for(BrowserTab t:tabs){
            LinearLayout r=row();GradientDrawable shape=round(t==activeTab?field:railColor,10);if(t==activeTab)shape.setStroke(dp(1),dark?0xFF55515D:0xFFD8CED4);r.setBackground(shape);
            TextView title=label(t.title,13,t==activeTab?text:muted);title.setPadding(dp(12),0,dp(6),0);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setOnClickListener(v->activateTab(t));r.addView(title,new LinearLayout.LayoutParams(dp(148),dp(44)));
            CinemaIcon sound=icon(t.muted?"mute":"volume",t.muted?"取消网页静音":"静音此网页",v->toggleTabMute(t));sound.selected(t.muted);sound.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));r.addView(sound);
            CinemaIcon close=icon("close","关闭标签页",v->closeTab(t));close.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));r.addView(close);
            LinearLayout.LayoutParams tabLayout=new LinearLayout.LayoutParams(-2,dp(46));tabLayout.setMargins(0,0,dp(5),0);tabsRow.addView(r,tabLayout);
        }
        CinemaIcon add=icon("plus","新建标签页",v->newTab(Navigation.HOME));add.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(44)));tabsRow.addView(add);updateMuteIcon();updateSidebarSelection();
    }
    public void restoreTabMute(boolean value){if(activeTab==null)return;activeTab.muted=value;if(activeTab.view!=null&&WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO))WebViewCompat.setAudioMuted(activeTab.view,value);rebuildTabs();}
    public boolean isTabMuted(){return activeTab!=null&&activeTab.muted;}
    private void updateMuteIcon(){if(compactMute!=null){compactMute.glyph(isTabMuted()?"mute":"volume",isTabMuted()?"取消网页静音":"静音当前网页");compactMute.selected(isTabMuted());}}
    private void toggleTabMute(BrowserTab tab){
        if(tab==null)return;
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)){notice("当前网页组件不支持独立网页静音，可使用播放器音量。",true);return;}
        boolean value=!tab.muted;
        try{if(tab.view!=null)WebViewCompat.setAudioMuted(tab.view,value);tab.muted=value;hideHint();rebuildTabs();}
        catch(RuntimeException error){notice("网页静音未生效，可使用播放器音量。",true);}
    }
    private void updateSidebarSelection(){
        if(sideNav==null)return;String current=activeTab==null?pendingUrl:activeTab.url;
        int best=-1,length=-1;
        for(int i=0;i<sideNav.getChildCount();i++){View child=sideNav.getChildAt(i);if(!(child.getTag() instanceof JSONObject))continue;JSONObject item=(JSONObject)child.getTag();String target=item.optString("url");if(!"link".equals(item.optString("action"))||target.isEmpty()||current==null)continue;boolean match=target.equals(Navigation.HOME)?current.split("[?#]",2)[0].equals(target):current.startsWith(target);if(match&&target.length()>length){best=i;length=target.length();}}
        for(int i=0;i<sideNav.getChildCount();i++)if(sideNav.getChildAt(i) instanceof CinemaIcon)((CinemaIcon)sideNav.getChildAt(i)).selected(i==best);
    }
    /** Keep background WebViews attached and visible behind the selected page for media playback. */
    private void foregroundTab(){
        if(web==null)return;
        ArrayList<View> chrome=new ArrayList<>();for(int i=0;i<stage.getChildCount();i++){View child=stage.getChildAt(i);if(!(child instanceof WebView))chrome.add(child);}
        web.bringToFront();for(View child:chrome)child.bringToFront();
        web.setVisibility(VISIBLE);web.setEnabled(true);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    private void activateTab(BrowserTab t){if(t==activeTab)return;closeOverlay();leaveFocus();removeCallbacks(loadTimeout);if(web!=null){web.clearFocus();web.setEnabled(false);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}activeTab=t;pendingUrl=t.url;web=t.view;pageGeneration++;pageError=false;pagePublished=web!=null;starting=web!=null;loading.setVisibility(GONE);progress.setVisibility(GONE);address.setText(pendingUrl);if(web==null)startBrowsing();else{foregroundTab();if(!paused)web.onResume();web.getSettings().setAlgorithmicDarkeningAllowed(dark&&!Navigation.isBili(pendingUrl));adaptPage();if(web.getProgress()<100)beginPage();}rebuildTabs();}
    private void newTab(String url){if(tabs.size()>=6){notice("最多同时打开 6 个标签页");return;}BrowserTab t=new BrowserTab(url);tabs.add(t);activateTab(t);}
    private void closeTab(BrowserTab t){if(t==activeTab){if(tabs.size()==1)tabs.add(new BrowserTab(Navigation.HOME));int index=tabs.indexOf(t);activateTab(tabs.get(index==0?1:index-1));}tabs.remove(t);if(t.view!=null){stage.removeView(t.view);t.view.stopLoading();t.view.destroy();t.view=null;}rebuildTabs();}

    private void palette(){bg=dark?0xFF101014:0xFFFFFFFF;railColor=dark?0xFF1B1B21:0xFFF3F3F7;text=dark?0xFFE7E8ED:0xFF252A34;muted=dark?0xFF9CA0AC:0xFF8A909C;field=dark?0xFF292B34:0xFFF1F2F4;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(getContext());v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout column(){LinearLayout v=row();v.setOrientation(LinearLayout.VERTICAL);return v;}
    private void space(LinearLayout v,int size){v.addView(new View(getContext()),new LinearLayout.LayoutParams(dp(size),dp(size)));}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String s,int size,int color){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private CinemaIcon icon(String name,String hint,OnClickListener action){CinemaIcon v=new CinemaIcon(getContext(),name,hint,dark,this,action);v.setLayoutParams(new LinearLayout.LayoutParams(dp(52),dp(52)));icons.add(v);return v;}
    @Override public void showHint(View anchor,String hint){if(disposed||!anchor.isShown())return;if(tooltip.getVisibility()==VISIBLE&&hint.contentEquals(tooltip.getText()))return;tooltip.setText(hint);FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);position.leftMargin=dp(focus?34:106);position.bottomMargin=dp(12);position.rightMargin=dp(18);tooltip.setLayoutParams(position);status.setAlpha(0f);progress.setAlpha(0f);tooltip.setVisibility(VISIBLE);tooltip.bringToFront();}
    @Override public void hideHint(){tooltip.setVisibility(GONE);status.setAlpha(1f);progress.setAlpha(1f);}
    private void loadingSlow(){if(!disposed&&web!=null&&!pagePublished){pageLoadTimedOut=true;progress.setVisibility(GONE);notice("网页加载较慢，可刷新重试。",true);}}
    private void beginPage(){pageLoadTimedOut=false;videoPlaying=false;host.cursorVisible(true);pageGeneration++;pageFinished=false;pagePublished=false;pageError=false;loading.setVisibility(GONE);if(web!=null){web.setAlpha(1f);web.setEnabled(true);}progress.setVisibility(VISIBLE);removeCallbacks(loadTimeout);postDelayed(loadTimeout,20000);}
    private void publishPage(int generation){if(disposed||web==null||generation!=pageGeneration||pageError)return;boolean firstPublication=!pagePublished;pagePublished=true;removeCallbacks(loadTimeout);loading.setVisibility(GONE);web.setAlpha(1f);web.setEnabled(true);progress.setVisibility(GONE);if(firstPublication){notice("");record("history");}CookieManager.getInstance().flush();activeTab.url=url();activeTab.title=web.getTitle()==null?"网页":web.getTitle();rebuildTabs();if(!address.hasFocus())address.setText(url());Log.i("CinemaBrowser","BROWSER_PAGE_VISIBLE");if(diagnostic&&firstPublication)postDelayed(this::snapshot,900);}
    private void monitorPage(){}
    /** Keep the native panel's first frame independent of WebView initialization. */
    public void startBrowsing(){if(disposed||starting)return;starting=true;Log.i("CinemaBrowser","WEB_CREATE_BEGIN immersive="+immersive);try{
        web=new WebView(getContext());web.setBackgroundColor(bg);activeTab.view=web;if(WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO))WebViewCompat.setAudioMuted(web,activeTab.muted);else activeTab.muted=false;updateMuteIcon();final WebView owner=web;stage.addView(web,0,new FrameLayout.LayoutParams(-1,-1));foregroundTab();mobileAgent=web.getSettings().getUserAgentString();configure(web);installEarlyTheme(activeTab);setAgent();
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return r.isForMainFrame()&&block(r.getUrl().toString());}
            @Override public void onPageStarted(WebView v,String url,Bitmap b){for(BrowserTab tab:tabs)if(tab.view==v){tab.url=url;break;}if(v!=web)return;pendingUrl=url;activeTab.url=url;beginPage();if(!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)){try(InputStream in=getContext().getAssets().open("theme-start.js")){v.evaluateJavascript(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("DARK",dark?"true":"false"),null);}catch(IOException ignored){}}if(!address.hasFocus())address.setText(url);}
            @Override public void onPageCommitVisible(WebView v,String url){if(disposed||v!=web||pageError)return;adaptPage();publishPage(pageGeneration);}
            @Override public void onPageFinished(WebView v,String url){if(disposed)return;adaptPage(v);applyPageState(v);if(v!=web||pageError)return;pendingUrl=url;pageFinished=true;publishPage(pageGeneration);Log.i("CinemaBrowser","WEB_PAGE_FINISHED host="+Uri.parse(url).getHost());}
            // Let WebView handle resource fetches and cache semantics; synthetic empty
            // prefetch responses can interfere with resources used by later navigation.

            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(v==web&&r.isForMainFrame()){pageError=true;pageFinished=false;removeCallbacks(pageMonitor);removeCallbacks(loadTimeout);progress.setVisibility(GONE);notice("网页加载失败："+e.getDescription(),true);loading.setVisibility(GONE);}}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();if(v!=web)return;pageError=true;pageFinished=false;removeCallbacks(pageMonitor);removeCallbacks(loadTimeout);progress.setVisibility(GONE);notice("证书校验失败，请检查网络和时间。",true);loading.setVisibility(GONE);}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onReceivedTitle(WebView v,String title){for(BrowserTab t:tabs)if(t.view==v){t.title=title==null?"网页":title;t.url=v.getUrl()==null?t.url:v.getUrl();break;}rebuildTabs();}
            @Override public void onProgressChanged(WebView v,int value){if(v!=web)return;if(value==100&&!pageError&&!pagePublished)publishPage(pageGeneration);progress.setVisibility(pagePublished||pageError||pageLoadTimedOut?GONE:VISIBLE);}
            @Override public void onShowCustomView(View v,CustomViewCallback callback){if(owner!=web||fullscreenView!=null){callback.onCustomViewHidden();return;}hideHint();closeOverlay();fullscreenView=v;fullscreenCallback=callback;web.setVisibility(GONE);stage.addView(v,new FrameLayout.LayoutParams(-1,-1));focus=true;updateChrome();Log.i("CinemaBrowser","VIDEO_FULLSCREEN_ENTER");}
            @Override public void onHideCustomView(){if(owner==web)leaveFocus();}
            @Override public boolean onCreateWindow(WebView v,boolean dialog,boolean gesture,Message msg){if(!gesture||v!=web)return false;WebView popup=new WebView(getContext());popups.add(popup);popup.setWebViewClient(new WebViewClient(){private void route(String url){if("about:blank".equals(url))return;if(!block(url))load(url);popup.post(()->releasePopup(popup));}@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){route(r.getUrl().toString());return true;}@Override public void onPageStarted(WebView v,String url,Bitmap b){popup.stopLoading();route(url);}});((WebView.WebViewTransport)msg.obj).setWebView(popup);msg.sendToTarget();popup.postDelayed(()->releasePopup(popup),15000);return true;}
        });web.setDownloadListener((u,a,d,m,l)->notice("当前版本暂不支持文件下载。"));web.loadUrl(pendingUrl);if(paused)web.onPause();Log.i("CinemaBrowser","WEB_CREATE_COMPLETE");if(restoreBatch)post(this::restoreOtherTabs);
    }catch(RuntimeException e){Log.e("CinemaBrowser","WEB_CREATE_FAILED",e);progress.setVisibility(GONE);notice("网页组件启动失败，可用顶栏刷新重试。",true);starting=false;if(web!=null){stage.removeView(web);web.destroy();activeTab.view=null;web=null;}}}
    private void configure(WebView v){WebSettings s=v.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setSupportMultipleWindows(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);s.setAlgorithmicDarkeningAllowed(dark&&!Navigation.isBili(pendingUrl));CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(v,true);}
    private void setAgent(){String agent=mobileAgent;if(prefs.getBoolean("desktop",true)){Matcher m=Pattern.compile("Chrome/([0-9.]+)").matcher(agent);agent="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"+(m.find()?m.group(1):"120.0.0.0")+" Safari/537.36";}web.getSettings().setUserAgentString(agent);}
    private void installEarlyTheme(BrowserTab tab){
        if(tab.view==null)return;
        if(tab.themeStart!=null){tab.themeStart.remove();tab.themeStart=null;}
        try(InputStream in=getContext().getAssets().open("theme-start.js")){
            String script=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("DARK",dark?"true":"false");
            if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))tab.themeStart=WebViewCompat.addDocumentStartJavaScript(tab.view,script,new HashSet<>(Arrays.asList("https://bilibili.com","https://*.bilibili.com")));
        }catch(IOException e){Log.w("CinemaBrowser","Early theme unavailable",e);}
    }
    private void adaptPage(){adaptPage(web);}
    private void adaptPage(WebView target){if(target==null)return;target.getSettings().setAlgorithmicDarkeningAllowed(dark&&!Navigation.isBili(target.getUrl()==null?pendingUrl:target.getUrl()));try{if(layoutScript==null){try(InputStream in=getContext().getAssets().open("client-layout.js")){layoutScript=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}if(homeScript==null){try(InputStream in=getContext().getAssets().open("home-layout.js")){homeScript=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}target.evaluateJavascript(layoutScript.replace("DARK",dark?"true":"false").replace("FILTER_ADS",prefs.getBoolean("filterAds",true)?"true":"false"),null);target.evaluateJavascript(homeScript.replace("HOME_LAYOUT",prefs.getBoolean("homeLayout",true)?"true":"false").replace("HIDE_PROMOTIONS",prefs.getBoolean("hidePromotions",false)?"true":"false").replace("HIDE_CAROUSEL",prefs.getBoolean("hideCarousel",true)?"true":"false"),null);}catch(IOException e){notice("布局资源读取失败，请重新安装应用。");}}
    private void toggleTheme(){dark=!dark;prefs.edit().putBoolean("dark",dark).apply();palette();setBackgroundColor(bg);sidebar.setBackgroundColor(railColor);header.setBackgroundColor(railColor);compact.setBackgroundColor(railColor);stage.setBackground(round(bg,14));loading.setBackgroundColor(bg);rebuildSidebar();address.setTextColor(text);address.setHintTextColor(muted);address.setBackground(round(field,18));rebuildTabs();status.setTextColor(muted);status.setBackground(round(railColor,8));progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(muted));for(CinemaIcon i:icons)i.dark(dark);for(BrowserTab t:tabs)if(t.view!=null){installEarlyTheme(t);t.view.setBackgroundColor(bg);t.view.evaluateJavascript("window.__questBrowser&&window.__questBrowser.theme("+dark+")",null);t.view.getSettings().setAlgorithmicDarkeningAllowed(dark&&!Navigation.isBili(t.url));}if(web!=null)adaptPage();if(overlay!=null)showSettings();}
    private void navigate(){try{load(Navigation.resolve(address.getText().toString()));address.clearFocus();((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);}catch(IllegalArgumentException e){notice(e.getMessage());}}
    private boolean block(String url){if(Navigation.isWeb(url))return false;notice("请输入有效的 HTTPS 网址。");return true;}
    private boolean capturingPage,restoreBatch;
    public void capturePage(android.webkit.ValueCallback<String> callback){
        if(capturingPage)return;capturingPage=true;
        ArrayList<BrowserTab> snapshot=new ArrayList<>(tabs);JSONArray entries=new JSONArray();JSONObject session=new JSONObject();
        try{session.put("tabs",entries).put("selected",Math.max(0,snapshot.indexOf(activeTab)));for(BrowserTab tab:snapshot){JSONObject item=new JSONObject().put("url",tab.view!=null&&Navigation.isWeb(tab.view.getUrl())?tab.view.getUrl():tab.url).put("title",tab.title).put("muted",tab.muted);if(tab.pageState!=null)item.put("state",new JSONObject(tab.pageState));entries.put(item);}}catch(JSONException ignored){}
        final boolean[] done={false};final int[] remaining={snapshot.size()};Runnable finish=()->{if(done[0])return;done[0]=true;capturingPage=false;callback.onReceiveValue(session.toString());};postDelayed(finish,1800);
        for(int i=0;i<snapshot.size();i++){BrowserTab tab=snapshot.get(i);final int index=i;if(tab.view==null||tab.pageState!=null){if(--remaining[0]==0){removeCallbacks(finish);finish.run();}continue;}
            tab.view.evaluateJavascript("JSON.stringify((()=>{const v=document.querySelector('.bpx-player-container video,#bilibili-player video,.bilibili-player video');return {url:location.href,x:scrollX,y:scrollY,time:v&&Number.isFinite(v.currentTime)?v.currentTime:null,playing:!!v&&!v.paused,volume:v?v.volume:1,muted:v? v.muted:false,rate:v?v.playbackRate:1}})())",raw->{if(done[0])return;try{Object value=new JSONTokener(raw).nextValue();if(value instanceof String){JSONObject state=new JSONObject((String)value);JSONObject item=entries.getJSONObject(index);if(Navigation.isWeb(state.optString("url")))item.put("url",state.getString("url")).put("state",state);}}catch(Exception ignored){}if(--remaining[0]==0){removeCallbacks(finish);finish.run();}});
        }
        if(snapshot.isEmpty())finish.run();
    }
    public void restorePage(String state){if(state==null)return;try{
        JSONObject session=new JSONObject(state);JSONArray entries=session.optJSONArray("tabs");
        if(entries==null){if(Navigation.isWeb(session.optString("url")))activeTab.pageState=session.toString();return;}
        ArrayList<BrowserTab> restored=new ArrayList<>();for(int i=0;i<Math.min(entries.length(),6);i++){JSONObject item=entries.getJSONObject(i);if(!Navigation.isWeb(item.optString("url")))continue;BrowserTab tab=new BrowserTab(item.getString("url"));tab.title=item.optString("title","网页");tab.muted=item.optBoolean("muted",false);JSONObject saved=item.optJSONObject("state");tab.pageState=saved==null?null:saved.toString();restored.add(tab);}
        if(restored.isEmpty())return;boolean hadWeb=web!=null;
        for(BrowserTab tab:tabs)if(tab.view!=null){stage.removeView(tab.view);tab.view.destroy();}
        tabs.clear();tabs.addAll(restored);activeTab=tabs.get(Math.max(0,Math.min(session.optInt("selected",0),tabs.size()-1)));pendingUrl=activeTab.url;web=null;starting=false;restoreBatch=true;address.setText(pendingUrl);rebuildTabs();if(hadWeb||!immersive)post(this::startBrowsing);
    }catch(JSONException ignored){}}
    private void restoreOtherTabs(){if(!restoreBatch||disposed)return;restoreBatch=false;BrowserTab selected=activeTab;for(BrowserTab tab:new ArrayList<>(tabs))if(tab!=selected&&tab.view==null)activateTab(tab);if(activeTab!=selected)activateTab(selected);}
    private void applyPageState(WebView owner){BrowserTab tab=null;for(BrowserTab item:tabs)if(item.view==owner){tab=item;break;}if(tab==null||tab.pageState==null)return;String value=tab.pageState;tab.pageState=null;
        try(InputStream in=getContext().getAssets().open("restore-page.js")){owner.evaluateJavascript(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("PAGE_STATE",value),null);}catch(IOException ignored){}
    }
    public String url(){String u=web==null?pendingUrl:web.getUrl();return Navigation.isWeb(u)?u:pendingUrl;}
    public void load(String url){if(disposed||!Navigation.isWeb(url))return;pendingUrl=url;hideHint();closeOverlay();leaveFocus();beginPage();if(web!=null)web.loadUrl(url);else startBrowsing();}
    private void reload(){if(web==null){startBrowsing();return;}leaveFocus();beginPage();web.reload();}
    public void notice(String s){notice(s,false);}
    private void notice(String s,boolean persistent){String message=s==null?"":s.trim();if(message.isEmpty()){removeCallbacks(hideNotice);status.setVisibility(GONE);return;}long now=android.os.SystemClock.uptimeMillis();if(message.equals(lastNotice)&&now-lastNoticeAt<12000)return;lastNotice=message;lastNoticeAt=now;removeCallbacks(hideNotice);status.setText(message);status.setVisibility(VISIBLE);status.bringToFront();if(!persistent)postDelayed(hideNotice,3500);}
    public void modeLabel(String s){modeText=s;if(geometryLabel!=null)geometryLabel.setText(s);if(!curveDragging){if(curvatureControl!=null)curvatureControl.setProgress(host.curvature());if(curveValue!=null)curveValue.setText(curvatureText(host.curvature()));}}
    public void enterFocus(){if(web==null)return;closeOverlay();hideHint();focus=true;updateChrome();web.evaluateJavascript("(function(){var v=document.querySelector('video');if(!v)return;function expand(){if(window.__questCinemaFocus)return;var n=v.closest('.bpx-player-container')||v.parentElement;window.__questCinemaFocus={node:n,style:n.style.cssText,overflow:document.body.style.overflow};n.style.cssText+=';position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;z-index:2147483646!important;background:black!important;';document.body.style.overflow='hidden';}if(document.fullscreenElement)return;try{var target=v.closest('.bpx-player-container,#bilibili-player,.bilibili-player')||v;var p=target.requestFullscreen();if(p&&p.catch)p.catch(expand)}catch(e){expand()}})()",null);}
    public void leaveFocus(){if(web!=null&&!disposed)web.evaluateJavascript("(function(){var f=window.__questCinemaFocus;if(f){f.node.style.cssText=f.style;document.body.style.overflow=f.overflow;delete window.__questCinemaFocus}})()",null);if(fullscreenView!=null){stage.removeView(fullscreenView);fullscreenView=null;if(web!=null)web.setVisibility(VISIBLE);WebChromeClient.CustomViewCallback cb=fullscreenCallback;fullscreenCallback=null;if(cb!=null)cb.onCustomViewHidden();}focus=false;updateChrome();}
    private void updateChrome(){sidebar.setVisibility(focus?GONE:VISIBLE);header.setVisibility(focus?GONE:VISIBLE);tabStrip.setVisibility(focus?GONE:VISIBLE);compact.setVisibility(focus?VISIBLE:GONE);FrameLayout.LayoutParams position=(FrameLayout.LayoutParams)status.getLayoutParams();position.leftMargin=dp(focus?34:106);status.setLayoutParams(position);FrameLayout.LayoutParams indicator=(FrameLayout.LayoutParams)progress.getLayoutParams();indicator.leftMargin=dp(focus?12:84);progress.setLayoutParams(indicator);}
    public boolean back(){if(overlay!=null){closeOverlay();return true;}if(focus){leaveFocus();return true;}if(web!=null&&web.canGoBack()){web.goBack();return true;}return false;}
    private void webBack(){if(web!=null&&web.canGoBack()){closeOverlay();leaveFocus();beginPage();web.goBack();}}
    private void forward(){if(web!=null&&web.canGoForward()){closeOverlay();leaveFocus();beginPage();web.goForward();}}
    private void showSearch(){closeOverlay();leaveFocus();address.requestFocus();((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(address,InputMethodManager.SHOW_IMPLICIT);}
    private void showChannels(){LinearLayout p=sheet("分类");channel(p,"直播","live","https://live.bilibili.com/");channel(p,"追番","tv","https://www.bilibili.com/anime/");channel(p,"影视","film","https://www.bilibili.com/movie/");}
    private void channel(LinearLayout p,String name,String glyph,String url){LinearLayout r=row();r.addView(icon(glyph,name,v->load(url)));r.addView(label(name,17,text));p.addView(r);}
    private void record(String key){if(web==null)return;String u=url();if(!Navigation.isWeb(u))return;try{JSONArray old=new JSONArray(prefs.getString(key,"[]")),next=new JSONArray();JSONObject item=new JSONObject();item.put("url",u);item.put("title",web.getTitle()==null?u:web.getTitle());next.put(item);for(int i=0;i<old.length()&&next.length()<50;i++){JSONObject o=old.getJSONObject(i);if(!u.equals(o.optString("url")))next.put(o);}prefs.edit().putString(key,next.toString()).apply();}catch(JSONException ignored){}}
    private LinearLayout sheet(String title){hideHint();closeOverlay();ScrollView scroll=new ScrollView(getContext());scroll.setFillViewport(true);scroll.setBackgroundColor(bg);LinearLayout p=column();p.setGravity(Gravity.TOP);p.setPadding(dp(32),dp(20),dp(32),dp(24));scroll.addView(p);LinearLayout top=row();top.addView(label(title,23,text),new LinearLayout.LayoutParams(0,dp(64),1));top.addView(icon("close","关闭",v->closeOverlay()));p.addView(top);overlay=scroll;stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));if(android.animation.ValueAnimator.areAnimatorsEnabled()){scroll.setAlpha(0);scroll.setTranslationY(dp(12));scroll.animate().alpha(1).translationY(0).setDuration(180).start();}return p;}
    private void closeOverlay(){geometryLabel=null;curvatureControl=null;curveValue=null;if(overlay!=null){final View old=overlay;icons.removeIf(i->{ViewParent p=i.getParent();while(p instanceof View){if(p==old)return true;p=p.getParent();}return false;});old.animate().cancel();stage.removeView(overlay);overlay=null;}}
    private void showLibrary(String title,String key){LinearLayout p=sheet(title);if("bookmarks".equals(key))p.addView(icon("bookmark","收藏当前页",v->{record("bookmarks");showLibrary(title,key);}));try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));if(a.length()==0)p.addView(label("还没有记录。",16,muted));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);String u=o.getString("url");LinearLayout r=row();TextView t=label(o.optString("title",u),16,text);t.setSingleLine();t.setEllipsize(android.text.TextUtils.TruncateAt.END);r.addView(t,new LinearLayout.LayoutParams(0,dp(64),1));r.addView(icon("play","打开视频",v->load(u)));p.addView(r);}p.addView(icon("clear","清空"+title,v->{prefs.edit().remove(key).apply();showLibrary(title,key);}));}catch(JSONException ignored){}}
    private void openCinema(){if(immersive)openCinemaSettings();else host.mode("cinema");}
    private String curvatureText(int value){return value==0?"0% · 平面":value+"% · "+Math.round(value*1.2f)+"°";}
    public void openCinemaSettings(){LinearLayout p=sheet("影院调节");geometryLabel=label(modeText,13,muted);p.addView(geometryLabel);LinearLayout title=row();title.addView(label("曲率",17,text),new LinearLayout.LayoutParams(0,dp(56),1));curveValue=label(curvatureText(host.curvature()),15,muted);title.addView(curveValue);p.addView(title);curvatureControl=new DragSeekBar(getContext());curvatureControl.setMax(100);curvatureControl.setProgress(host.curvature());curvatureControl.setContentDescription("屏幕曲率，0 为平面，100 为最大曲率");curvatureControl.setProgressTintList(android.content.res.ColorStateList.valueOf(pink));curvatureControl.setThumbTintList(android.content.res.ColorStateList.valueOf(pink));p.addView(curvatureControl,new LinearLayout.LayoutParams(-1,dp(72)));curvatureControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(curveValue!=null)curveValue.setText(curvatureText(value)+(curveDragging?" · 松开应用":""));if(user&&!curveDragging&&!disposed)host.curvature(value);}public void onStartTrackingTouch(SeekBar bar){curveDragging=true;}public void onStopTrackingTouch(SeekBar bar){curveDragging=false;if(!disposed)host.curvature(bar.getProgress());if(curveValue!=null)curveValue.setText(curvatureText(bar.getProgress()));}});p.addView(label("0% 平面 · 25% 轻弯 · 75% 标准 · 100% 最大",13,muted));if(immersive){settingRow(p,"屏幕大小","缩小","grow-","放大","grow+");settingRow(p,"观看距离","靠近","near","远离","far");settingRow(p,"屏幕高度","降低","down","抬高","up");p.addView(icon("reset","重置屏幕并居中",v->host.adjust("reset")));}else p.addView(icon("curve","使用此曲率进入影院",v->host.mode("cinema")));}
    private void showPlayerControls(){LinearLayout p=sheet("播放与音量");settingToggle(p,"网页静音（独立于播放器音量）",isTabMuted(),()->{toggleTabMute(activeTab);});p.addView(icon("play","播放 / 暂停",v->{if(web!=null)web.evaluateJavascript("(function(){var v=document.querySelector('.bpx-player-container video,#bilibili-player video,video');if(v){if(v.paused)v.play().catch(()=>{});else v.pause();}})()",null);}));TextView value=label("音量",16,text);p.addView(value);SeekBar bar=new DragSeekBar(getContext());bar.setMax(100);bar.setContentDescription("视频音量，0 为静音");bar.setProgress(50);final boolean[] touched={false};p.addView(bar,new LinearLayout.LayoutParams(-1,dp(64)));if(web!=null)web.evaluateJavascript("(function(){var v=document.querySelector('.bpx-player-container video,#bilibili-player video,video');return v?(v.muted?0:Math.round(v.volume*100)):50})()",v->{try{if(!touched[0]&&bar.isAttachedToWindow())bar.setProgress(Integer.parseInt(v));}catch(Exception ignored){}});final WebView volumeOwner=web;final float[] pendingVolume={.5f};final boolean[] volumeQueued={false};Runnable applyVolume=()->{volumeQueued[0]=false;if(!disposed&&volumeOwner!=null&&web==volumeOwner)volumeOwner.evaluateJavascript("window.__questBrowser&&window.__questBrowser.volume("+pendingVolume[0]+")",null);};bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){value.setText("音量 "+n+"%");if(user&&web!=null){touched[0]=true;pendingVolume[0]=n/100f;if(!volumeQueued[0]){volumeQueued[0]=true;postDelayed(applyVolume,40);}}}public void onStartTrackingTouch(SeekBar b){touched[0]=true;}public void onStopTrackingTouch(SeekBar b){removeCallbacks(applyVolume);pendingVolume[0]=b.getProgress()/100f;applyVolume.run();}});}
    private void settingAction(LinearLayout p,String glyph,String title,Runnable action){LinearLayout r=row();r.addView(icon(glyph,title,v->action.run()));TextView name=label(title,16,text);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setOnClickListener(v->action.run());r.addView(name,new LinearLayout.LayoutParams(0,dp(60),1));p.addView(r);}
    private void showSettings(){LinearLayout p=sheet("设置");settingAction(p,"feed","自定义侧栏",this::showSidebarEditor);settingAction(p,"curve","影院调节",this::openCinemaSettings);settingAction(p,"theme","切换明暗主题",this::toggleTheme);
        Button originalHome=new Button(getContext());originalHome.setText("首页异常：恢复原网页并重新加载");originalHome.setOnClickListener(v->{prefs.edit().putBoolean("homeLayout",false).putBoolean("filterAds",false).putBoolean("hidePromotions",false).putBoolean("hideCarousel",false).apply();closeOverlay();load(Navigation.HOME);});settingAction(p,"reset","恢复原网页并重新加载",()->originalHome.performClick());
        settingToggle(p,"客户端式首页",prefs.getBoolean("homeLayout",true),()->{prefs.edit().putBoolean("homeLayout",!prefs.getBoolean("homeLayout",true)).apply();adaptPage();});
        settingToggle(p,"隐藏游戏、会员购和下载入口",prefs.getBoolean("hidePromotions",false),()->{prefs.edit().putBoolean("hidePromotions",!prefs.getBoolean("hidePromotions",false)).apply();adaptPage();});
        settingToggle(p,"隐藏首页轮播（也会隐藏普通推荐）",prefs.getBoolean("hideCarousel",true),()->{prefs.edit().putBoolean("hideCarousel",!prefs.getBoolean("hideCarousel",true)).apply();adaptPage();});
        settingToggle(p,"过滤广告卡片",prefs.getBoolean("filterAds",true),()->{prefs.edit().putBoolean("filterAds",!prefs.getBoolean("filterAds",true)).apply();adaptPage();});
        TextView note=label("图标悬停约半秒显示名称；鼠标、手柄射线、键盘焦点及长按均可查看。\n桌面与影院切换会重新载入当前网页，登录状态共用。\n网页播放器决定播放和清晰度。bili233 0.21.0 · 非官方应用。",14,muted);note.setPadding(0,dp(20),0,dp(16));p.addView(note);
        LinearLayout clear=row();clear.addView(icon("clear","清除登录和网页数据",v->{LinearLayout confirm=sheet("清除网页数据？");confirm.addView(label("将退出本应用登录，并清除网页缓存。",16,muted));Button b=new Button(getContext());b.setText("确认清除");b.setOnClickListener(a->{if(web==null)return;web.stopLoading();web.loadUrl("about:blank");CookieManager.getInstance().removeAllCookies(done->{if(disposed)return;CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();web.clearCache(true);web.clearHistory();load(Navigation.HOME);});});confirm.addView(b);}));clear.addView(label("清除登录与网页数据",16,text),new LinearLayout.LayoutParams(0,dp(56),1));p.addView(clear);
    }
    private void settingToggle(LinearLayout p,String title,boolean checked,Runnable action){
        LinearLayout r=row();r.setPadding(dp(10),dp(4),dp(10),dp(4));
        String glyph=title.contains("轮播")?"film":title.contains("首页")?"home":title.contains("广告")?"shield":title.contains("隐藏")?"eye":title.contains("静音")?"volume":"flat";
        FlatToggle toggle=new FlatToggle(getContext(),title,checked,dark,this,action);
        r.addView(icon(glyph,title,v->toggle.performClick()));
        TextView name=label(title,16,text);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setOnClickListener(v->toggle.performClick());r.addView(name,new LinearLayout.LayoutParams(0,dp(60),1));
        r.addView(toggle,new LinearLayout.LayoutParams(dp(76),dp(60)));p.addView(r);
    }
    private void settingRow(LinearLayout p,String title,String a,String actionA,String b,String actionB){LinearLayout r=row();r.addView(label(title,16,text),new LinearLayout.LayoutParams(0,dp(60),1));r.addView(icon(actionA.equals("down")?"down":"minus",a,v->host.adjust(actionA)));r.addView(icon(actionB.equals("up")?"up":"plus",b,v->host.adjust(actionB)));p.addView(r);}
    private void releasePopup(WebView v){if(popups.remove(v))v.destroy();}
    public void pause(){paused=true;removeCallbacks(playerPoll);host.cursorVisible(true);pointerVisible=false;pointerPressed=false;invalidate();removeCallbacks(pageMonitor);for(BrowserTab t:tabs)if(t.view!=null){t.view.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause()})",null);t.view.onPause();}CookieManager.getInstance().flush();}
    public void resume(){paused=false;for(BrowserTab t:tabs)if(t.view!=null)t.view.onResume();if(immersive){removeCallbacks(playerPoll);post(playerPoll);}if(web!=null){web.onResume();if(pageFinished){removeCallbacks(pageMonitor);post(pageMonitor);}}}
    public void dispose(){disposed=true;removeCallbacks(playerPoll);host.cursorVisible(true);removeCallbacks(hideNotice);removeCallbacks(loadTimeout);removeCallbacks(pageMonitor);hideHint();leaveFocus();for(WebView p:popups)p.destroy();popups.clear();for(BrowserTab t:tabs)if(t.view!=null){stage.removeView(t.view);t.view.stopLoading();t.view.destroy();t.view=null;}web=null;tabs.clear();}
    /** Draw on the same compositor layer as the hit surface, including above native fullscreen. */
    private void trackPointer(MotionEvent event){if(!immersive)return;int action=event.getActionMasked();float x=event.getX(),y=event.getY();boolean deliberate=action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_SCROLL||Math.hypot(x-activityPointerX,y-activityPointerY)>dp(5);if(deliberate){activityPointerX=x;activityPointerY=y;pointerActivity=android.os.SystemClock.uptimeMillis();host.cursorVisible(true);}pointerVisible=action!=MotionEvent.ACTION_HOVER_EXIT&&action!=MotionEvent.ACTION_CANCEL&&(!videoPlaying||pointerPressed||android.os.SystemClock.uptimeMillis()-pointerActivity<2500);pointerX=x;pointerY=y;if(action==MotionEvent.ACTION_DOWN)pointerPressed=true;if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)pointerPressed=false;invalidate();}
    @Override public boolean dispatchHoverEvent(MotionEvent event){trackPointer(event);return super.dispatchHoverEvent(event);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){if(event.isFromSource(InputDevice.SOURCE_CLASS_POINTER))trackPointer(event);return super.dispatchGenericMotionEvent(event);}
    private boolean consumeRecenterGesture;
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.isCtrlPressed()&&event.getKeyCode()==KeyEvent.KEYCODE_M){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)toggleTabMute(activeTab);return true;}if(immersive&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&host.recenterIfNeeded())return true;return super.dispatchKeyEvent(event);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){if(immersive&&host.movingWindow()){if(!consumeRecenterGesture){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();pointerPressed=false;}consumeRecenterGesture=true;return true;}if(immersive&&event.getActionMasked()==MotionEvent.ACTION_DOWN&&host.recenterIfNeeded())consumeRecenterGesture=true;if(consumeRecenterGesture){if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)consumeRecenterGesture=false;return true;}trackPointer(event);return super.dispatchTouchEvent(event);}
    @Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);if(!immersive||!pointerVisible)return;pointerPaint.setStyle(Paint.Style.FILL);pointerPaint.setColor(0xB0000000);canvas.drawCircle(pointerX,pointerY,dp(9),pointerPaint);pointerPaint.setColor(pointerPressed?pink:Color.WHITE);canvas.drawCircle(pointerX,pointerY,dp(pointerPressed?5:4),pointerPaint);pointerPaint.setStyle(Paint.Style.STROKE);pointerPaint.setStrokeWidth(dp(2));pointerPaint.setColor(Color.WHITE);canvas.drawCircle(pointerX,pointerY,dp(8),pointerPaint);}
    public void requestDiagnosticSnapshot(){if(BuildConfig.DEBUG)diagnostic=true;}
    /** ADB-only development actions for regression checks; no JavaScript bridge is exposed. */
    public void diagnosticAction(String action){
        if(!BuildConfig.DEBUG||action==null||disposed)return;
        switch(action){
            case "enter-curved":host.mode("curved");break;
            case "flat":host.mode("flat");break;
            case "curved":host.mode("curved");break;
            case "settings":showSettings();break;

            case "fullscreen":enterFocus();break;
            case "new-tab":newTab(Navigation.HOME);break;
            case "sidebar":showSidebarEditor();break;
            case "first-video":if(web!=null)web.evaluateJavascript("(function(){var a=document.querySelector('a[href*=\"/video/BV\"]');return a?a.href:null})()",value->{try{String url=new JSONArray("["+value+"]").getString(0);if(Navigation.isWeb(url))load(url);}catch(JSONException ignored){}});break;
            case "back":back();break;
            case "exit":host.exit();break;
            case "hover-curve":for(CinemaIcon i:icons){if("影院调节".contentEquals(i.getContentDescription())){long time=android.os.SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(time,time,MotionEvent.ACTION_HOVER_ENTER,20,20,0);event.setSource(InputDevice.SOURCE_MOUSE);i.dispatchGenericMotionEvent(event);event.recycle();break;}}break;
        }
        Log.i("CinemaBrowser","DIAGNOSTIC_ACTION "+action);
        diagnostic=true;postDelayed(this::snapshot,900);
    }

    private void snapshot(){if(!diagnostic||disposed||getWidth()==0||getHeight()==0)return;diagnostic=false;try{Bitmap b=Bitmap.createBitmap(getWidth(),getHeight(),Bitmap.Config.ARGB_8888);draw(new Canvas(b));File file=new File(getContext().getExternalFilesDir(null),immersive?"cinema-ui.png":"desktop-ui.png");try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();Log.i("CinemaBrowser","DIAGNOSTIC_SNAPSHOT_READY");}catch(Exception e){Log.w("CinemaBrowser","DIAGNOSTIC_SNAPSHOT_FAILED",e);}}
}
