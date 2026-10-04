package io.local.questwebcinema

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Gravity
import android.widget.Button
import android.graphics.Color as AndroidColor
import android.util.Log
import com.meta.spatial.core.*
import com.meta.spatial.runtime.*
import com.meta.spatial.toolkit.*
import com.meta.spatial.vr.VRFeature
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.isdk.IsdkPanelDimensions
import com.meta.spatial.isdk.IsdkSystem
import com.meta.spatial.isdk.IsdkDefaultCursorSystem
import com.meta.spatial.isdk.IsdkCurvedPanel
import java.util.Locale
import java.io.File
import kotlin.math.roundToInt
import com.meta.spatial.okhttp3.OkHttpAssetFetcher

/** One virtual Android display, with its surface reshaped in place between quad and cylinder. */
class CinemaActivity : AppSystemActivity(), CinemaBrowserView.Host {
    private var browser: CinemaBrowserView? = null
    private var panel: PanelSceneObject? = null
    private var screen: Entity? = null
    private var anchor = Pose()
    private var curved = true
    private var requestedCurved = true
    private var panelReady = false
    private var sceneFrames = 0
    @Volatile private var showCursor = true
    private var appliedCursor = true
    private val mainHandler = Handler(Looper.getMainLooper())
    private val startupTimeout = Runnable {
        if (!panelReady && !isFinishing && !exiting) exitWithError("影院启动超时，已返回桌面。")
    }
    private var width = 4.8f
    private var distance = 3.5f
    private var arc = 90f
    private var elevation = 0f
    private var exiting = false
    private var lastRecenter = 0L
    private val controllerQuery by lazy { Query.where { has(Controller.id) } }
    private var recoveryPanel: Entity? = null
    private var recoveryVisible = false
    @Volatile private var dragHand: Entity? = null
    private var dragAnchor = Pose()
    private var dragStartLocal = Vector3(0f)
    private var dragDistance = 3.5f

    override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this))

    override fun onCreate(savedInstanceState: Bundle?) {
        curved = false
        val prefs = getSharedPreferences("cinema", MODE_PRIVATE)
        width = prefs.getFloat("width", 4.8f).coerceIn(2.4f, 7.2f)
        distance = prefs.getFloat("distance", 3.5f).coerceIn(1.5f, 8f)
        arc = prefs.getInt("curvature", (prefs.getFloat("arc", 90f) / 1.2f).roundToInt()).coerceIn(0, 100) * 1.2f
        if (intent.getStringExtra("mode") == "flat") arc = 0f
        requestedCurved = arc > 0f
        curved = requestedCurved
        elevation = prefs.getFloat("elevation", 0f).coerceIn(-1f, 1f)
        super.onCreate(savedInstanceState)
        requestHandPermission()
        NetworkedAssetLoader.init(File(applicationContext.cacheDir.canonicalPath), OkHttpAssetFetcher())
        Log.i("CinemaScene", "ACTIVITY_CREATED requestedCurved=$requestedCurved")
        mainHandler.postDelayed(startupTimeout, 20000)
    }

    private fun radius() = if (arc > 0f) width / Math.toRadians(arc.toDouble()).toFloat() else 1f

    override fun registerPanels(): List<PanelRegistration> = listOf(
        ViewPanelRegistration(
            R.id.cinema_panel,
            dynamicViewCreator = { _, context ->
                CinemaBrowserView(context, this, true, intent.getStringExtra("url")).also { browser = it }
            },
            settingsCreator = {
                UIPanelSettings(
                    shape = if (requestedCurved) CylinderShapeOptions(radius(), width, width * 0.625f) else QuadShapeOptions(width, width * 0.625f),
                    display = DpDisplayOptions(width = 1440f, height = 900f),
                    rendering = UIPanelRenderOptions(PanelRenderMode.Layer(layerBlendType = PanelShapeLayerBlendType.OPAQUE)),
                    style = PanelStyleOptions(themeResourceId = R.style.AppTheme)
                )
            },
            panelSetupWithRootView = { _, scenePanel, _ ->
                panel = scenePanel
                Log.i("CinemaScene", "PANEL_DISPLAY_READY")
                browser?.post {
                    panelReady = true
                    mainHandler.removeCallbacks(startupTimeout)
                    curved = requestedCurved
                    reshape()
                    if (BuildConfig.DEBUG && intent.getBooleanExtra("diagnostic", false)) browser?.requestDiagnosticSnapshot()
                    browser?.postDelayed({ browser?.startBrowsing() }, 250)
                }
            }
        ),
        ViewPanelRegistration(
            R.id.recenter_panel,
            dynamicViewCreator = { _, context ->
                Button(context).apply {
                    text = "点按恢复中心"
                    textSize = 20f
                    setTextColor(AndroidColor.WHITE)
                    setBackgroundColor(AndroidColor.rgb(28, 28, 32))
                    gravity = Gravity.CENTER
                    setOnClickListener { restoreCenter() }
                }
            },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(0.85f, 0.22f),
                    display = DpDisplayOptions(width = 420f, height = 110f),
                    rendering = UIPanelRenderOptions(PanelRenderMode.Layer(layerBlendType = PanelShapeLayerBlendType.OPAQUE)),
                    style = PanelStyleOptions(themeResourceId = R.style.AppTheme)
                )
            }
        )
    )

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL)
        systemManager.tryFindSystem<IsdkSystem>()?.setScenePointerDistance(20f)
        scene.enablePassthrough(false)
        scene.enableHolePunching(false)
        scene.setBackfillColor(com.meta.spatial.core.Color4(0.008f, 0.01f, 0.015f, 1f))
        scene.setLightingEnvironment(Vector3(0.2f), Vector3(0f), Vector3(0f, -1f, 0f), 0f)
        systemManager.tryFindSystem<LocomotionSystem>()?.enableLocomotion(false)
        anchor = scene.getViewerPose().removePitchAndRoll()
        screen = Entity.create(listOf(Panel(R.id.cinema_panel), Transform(screenPose())))
        recoveryPanel = Entity.create(listOf(Panel(R.id.recenter_panel), Transform(Pose()), Visible(false)))
        Log.i("CinemaScene", "SCENE_READY screen=${screen?.id}")
    }

    private fun screenPose(): Pose {
        // Cylinder entities are located at the circle's center, unlike a quad's surface center.
        // Offset the origin so the center of the visible screen stays at the chosen distance.
        val originDistance = if (curved) distance - radius() else distance
        return anchor * Pose(Vector3(0f, elevation, originDistance), Quaternion())
    }

    override fun mode(mode: String) {
        if (mode == "flat") curvature(0)
        else if (mode == "curved") curvature(75)
        else browser?.openCinemaSettings()
    }

    override fun curvature(): Int = (arc / 1.2f).roundToInt().coerceIn(0, 100)
    override fun cursorVisible(visible: Boolean) { showCursor = visible }
    override fun movingWindow(): Boolean = dragHand != null
    override fun curvature(value: Int) {
        val nextArc = value.coerceIn(0, 100) * 1.2f
        if (nextArc == arc) return
        arc = nextArc
        curved = arc > 0f
        requestedCurved = curved
        if (panelReady) reshape()
    }

    override fun adjust(action: String) {
        when (action) {
            "grow+" -> width = (width + 0.4f).coerceAtMost(7.2f)
            "grow-" -> width = (width - 0.4f).coerceAtLeast(2.4f)
            "near" -> distance = (distance - 0.25f).coerceAtLeast(1.5f)
            "far" -> distance = (distance + 0.25f).coerceAtMost(8f)
            "curve+" -> { arc = (arc + 6f).coerceAtMost(120f); curved = arc > 0f }
            "curve-" -> { arc = (arc - 6f).coerceAtLeast(0f); curved = arc > 0f }
            "up" -> elevation = (elevation + 0.15f).coerceAtMost(1f)
            "down" -> elevation = (elevation - 0.15f).coerceAtLeast(-1f)
            "reset" -> { width = 4.8f; distance = 3.5f; arc = 90f; curved = true; elevation = 0f; anchor = scene.getViewerPose().removePitchAndRoll() }
        }
        reshape()
    }

    private fun reshape() {
        // A new shape config regenerates the collision mesh as well as the compositor layer.
        // Keep the Android virtual display alive, so playback and the input surface are retained.
        val previous = panel?.panelShapeConfig
        panel?.reshape(PanelShapeConfig(
            width = width, height = width * 0.625f,
            radiusForCylinderOrSphere = radius(),
            panelShapeType = if (curved) PanelShapeType.CYLINDER else PanelShapeType.QUAD,
            includeGlass = false,
            alphaMode = previous?.alphaMode ?: AlphaMode.OPAQUE,
            panelShader = previous?.panelShader ?: SceneMaterial.HOLE_PUNCH_PANEL_SHADER,
            layerConfig = previous?.layerConfig,
            layerBlendType = PanelShapeLayerBlendType.OPAQUE,
            enableTransparent = false
        ))
        screen?.setComponent(Transform(screenPose()))
        syncInputShape()
        getSharedPreferences("cinema", MODE_PRIVATE).edit()
            .putFloat("width", width).putFloat("distance", distance)
            .putFloat("arc", arc).putInt("curvature", curvature()).putFloat("elevation", elevation).apply()
        updateLabel()
        Log.i("CinemaScene", "SCREEN_CONFIG curved=$curved width=$width distance=$distance arc=$arc")
    }

    private fun updateLabel() {
        val text = String.format(Locale.CHINA, "屏幕 %.1f m · 距离 %.1f m", width, distance)
        browser?.modeLabel(text)
    }
    private fun syncInputShape() {
        val entity = screen ?: return
        val dimensions = entity.tryGetComponent<IsdkPanelDimensions>()
        val offset = dimensions?.localOffset ?: Vector2(0f, 0f)
        entity.setComponent(IsdkPanelDimensions(Vector2(width, width * 0.625f), offset))
        entity.setComponent(IsdkCurvedPanel(if (curved) arc else 0f))
        Log.i("CinemaScene", "INPUT_SHAPE width=$width height=${width * 0.625f} arc=${if (curved) arc else 0f}")
    }

    private fun requestHandPermission() {
        val permissions = listOf("horizonos.permission.HAND_TRACKING", "com.oculus.permission.HAND_TRACKING").filter { name ->
            try {
                val info = packageManager.getPermissionInfo(name, 0)
                (info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS &&
                    checkSelfPermission(name) != android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
        }
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 701)
    }

    override fun exit() = exitWithError(null)

    private fun exitWithError(error: String?) {
        if (exiting) return
        exiting = true
        browser?.pause()
        val desktop = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("url", browser?.url() ?: Navigation.HOME)
            if (error != null) putExtra("cinema_error", error)
        }
        val launch = PendingIntent.getActivity(this, 0, desktop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("extra_launch_in_home_pending_intent", launch)
        })
        finish()
    }

    @Deprecated("Android legacy back required by Quest")
    override fun onBackPressed() { if (browser?.back() != true) exit() }
    override fun onPause() { dragHand = null; browser?.pause(); super.onPause() }
    override fun onResume() { super.onResume(); browser?.resume() }
    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        if (BuildConfig.DEBUG && newIntent.hasExtra("diagnostic_action")) {
            browser?.post { browser?.diagnosticAction(newIntent.getStringExtra("diagnostic_action")) }
        }
    }
    override fun onSceneTick() {
        super.onSceneTick()
        sceneFrames++
        if (sceneFrames == 60) Log.i("CinemaScene", "SCENE_TICK_60 panelReady=$panelReady")
        if (!panelReady || exiting) return
        val cursor = showCursor || dragHand != null || recoveryVisible
        if (cursor != appliedCursor) {
            systemManager.tryFindSystem<IsdkDefaultCursorSystem>()?.enableInput(cursor)
            appliedCursor = cursor
        }
        handleGripDrag()
        val away = dragHand == null && viewAway()
        if (away != recoveryVisible) { recoveryVisible = away; recoveryPanel?.setComponent(Visible(away)) }
        if (away) {
            recoveryPanel?.setComponent(Transform(scene.getViewerPose() * Pose(Vector3(0f, -0.25f, 1.4f), Quaternion())))
            val pressed = controllerQuery.eval().filter { it.isLocal() }.any {
                val input = it.getComponent<Controller>()
                input.isActive && (input.buttonState and input.changedButtons and ButtonBits.AllButtonClickMask) != 0
            }
            if (pressed) recenterIfNeeded()
        }
    }
    private fun viewAway(): Boolean {
        val viewer = scene.getViewerPose()
        val center = anchor * Vector3(0f, elevation, distance)
        val direction = (center - viewer.t).normalize()
        return viewer.forward().normalize().dot(direction) < 0.642788f // 50 degrees
    }
    override fun recenterIfNeeded(): Boolean {
        if (!panelReady || exiting || dragHand != null || !viewAway()) return false
        val now = SystemClock.uptimeMillis()
        if (now - lastRecenter < 700) return false
        lastRecenter = now
        restoreCenter()
        Log.i("CinemaScene", "RECENTER_TO_VIEW")
        return true
    }
    private fun restoreCenter() {
        if (!panelReady || exiting) return
        dragHand = null
        anchor = scene.getViewerPose().copy()
        screen?.setComponent(Transform(screenPose()))
        recoveryVisible = false
        recoveryPanel?.setComponent(Visible(false))
    }
    private fun handleGripDrag() {
        val mask = ButtonBits.ButtonSqueezeL or ButtonBits.ButtonSqueezeR
        var hand = dragHand
        if (hand != null) {
            val input = hand.getComponent<Controller>()
            if (!input.isActive || (input.buttonState and mask) == 0) {
                dragHand = null
                getSharedPreferences("cinema", MODE_PRIVATE).edit().putFloat("distance", distance).apply()
                browser?.post { updateLabel() }
                Log.i("CinemaScene", "GRIP_DRAG_END distance=$distance")
                return
            }
        } else {
            hand = controllerQuery.eval().filter { it.isLocal() }.firstOrNull {
                val input = it.getComponent<Controller>()
                input.isActive && input.type == ControllerType.CONTROLLER &&
                    (input.buttonState and input.changedButtons and mask) != 0
            }
            if (hand == null) return
            dragHand = hand
            dragAnchor = anchor.copy()
            dragDistance = distance
            dragStartLocal = dragAnchor.inverse() * hand.getComponent<Transform>().transform.t
            Log.i("CinemaScene", "GRIP_DRAG_BEGIN")
        }
        val local = dragAnchor.inverse() * hand.getComponent<Transform>().transform.t
        val delta = (local - dragStartLocal) * 2f
        distance = (dragDistance + delta.z).coerceIn(1.5f, 8f)
        anchor = Pose(dragAnchor.t + dragAnchor.right() * delta.x + dragAnchor.up() * delta.y, dragAnchor.q)
        screen?.setComponent(Transform(screenPose()))
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && recenterIfNeeded()) return true
        return super.dispatchKeyEvent(event)
    }
    override fun onRecenter(isUserInitiated: Boolean) {
        super.onRecenter(isUserInitiated)
        if (isUserInitiated && panelReady) { anchor = scene.getViewerPose().copy(); screen?.setComponent(Transform(screenPose())) }
    }
    override fun onDestroy() { mainHandler.removeCallbacks(startupTimeout); browser?.dispose(); browser = null; panel = null; super.onDestroy() }
}
