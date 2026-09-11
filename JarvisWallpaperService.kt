package com.thunderfire.jarvis3d

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.Mat4
import com.google.android.filament.utils.scale
import com.google.android.filament.utils.translation
import java.nio.ByteBuffer
import kotlin.math.max

class JarvisWallpaperService : WallpaperService() {

    companion object {
        init {
            Filament.init()
        }
    }

    override fun onCreateEngine(): Engine {
        return JarvisEngine()
    }

    private inner class JarvisEngine : Engine(), SensorEventListener {

        private lateinit var uiHelper: UiHelper
        private lateinit var displayHelper: DisplayHelper

        private lateinit var filament: com.google.android.filament.Engine
        private lateinit var renderer: Renderer
        private lateinit var scene: Scene
        private lateinit var view: View
        private lateinit var camera: Camera

        private var swapChain: SwapChain? = null
        private var model: FilamentAsset? = null
        private var assetLoader: AssetLoader? = null
        private var resourceLoader: ResourceLoader? = null
        private var materialProvider: UbershaderProvider? = null

        private lateinit var sensorManager: SensorManager
        private var rotationSensor: Sensor? = null

        private var sensorPitch = 0f
        private var sensorRoll = 0f
        private var smoothPitch = 0f
        private var smoothRoll = 0f

        private var visible = false
        private var modelReady = false
        private var startTime = 0L

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!visible) return

                renderFrame(frameTimeNanos)

                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            surfaceHolder.setSizeFromLayout()

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

            displayHelper = DisplayHelper(this@JarvisWallpaperService)
            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
            uiHelper.renderCallback = SurfaceCallback()
            uiHelper.attachTo(surfaceHolder)

            setupFilament()
            setupScene()
            loadModel()
        }

        private fun setupFilament() {
            filament = com.google.android.filament.Engine.create()
            renderer = filament.createRenderer()
            scene = filament.createScene()
            view = filament.createView()

            val cameraEntity = EntityManager.get().create()
            camera = filament.createCamera(cameraEntity)

            view.scene = scene
            view.camera = camera

            // AMOLED black background.
            scene.skybox = Skybox.Builder()
                .color(0.0f, 0.0f, 0.0f, 1.0f)
                .build(filament)

            renderer.clearOptions = renderer.clearOptions.apply {
                clear = true
                discard = false
                clearColor = floatArrayOf(0f, 0f, 0f, 1f)
            }

            view.renderQuality = view.renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.MEDIUM
            }

            view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
                enabled = true
                quality = View.QualityLevel.MEDIUM
            }

            view.antiAliasing = View.AntiAliasing.FXAA

            camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
        }

        private fun setupScene() {
            // White key light.
            val keyLight = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.SUN)
                .color(1.0f, 1.0f, 1.0f)
                .intensity(70_000.0f)
                .direction(0.2f, -0.7f, -1.0f)
                .castShadows(true)
                .build(filament, keyLight)
            scene.addEntity(keyLight)

            // Green JARVIS-style rim light.
            val rimLight = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.POINT)
                .color(0.0f, 1.0f, 0.35f)
                .intensity(55_000.0f)
                .falloff(4.0f)
                .position(0.0f, 0.4f, 2.0f)
                .build(filament, rimLight)
            scene.addEntity(rimLight)
        }

        private fun loadModel() {
            try {
                val bytes = assets.open("rath.glb").use { it.readBytes() }
                val buffer = ByteBuffer.wrap(bytes)

                materialProvider = UbershaderProvider(filament)
                assetLoader = AssetLoader(
                    filament,
                    materialProvider,
                    EntityManager.get()
                )
                resourceLoader = ResourceLoader(filament, true)

                model = assetLoader!!.createAsset(buffer)
                    ?: throw IllegalStateException("Could not parse rath.glb")

                resourceLoader!!.loadResources(model!!)
                model!!.releaseSourceData()

                scene.addEntities(*model!!.entities)

                fitModel(model!!)
                modelReady = true
                startTime = System.nanoTime()
            } catch (t: Throwable) {
                modelReady = false
                android.util.Log.e("JARVIS3D", "Model load failed", t)
            }
        }

        private fun fitModel(asset: FilamentAsset) {
            val center = asset.boundingBox.center
            val half = asset.boundingBox.halfExtent

            val maxExtent = max(
                half[0],
                max(half[1], half[2])
            ) * 2.0f

            if (maxExtent <= 0.0001f) return

            val scaleFactor = 2.0f / maxExtent

            val center3 = Float3(center[0], center[1], center[2])
            val desiredCenter = Float3(0f, 0f, -4f)
            val translatedCenter = center3 - desiredCenter / scaleFactor

            val transform = scale(Float3(scaleFactor)) * translation(-translatedCenter)

            val instance = filament.transformManager.getInstance(asset.root)
            filament.transformManager.setTransform(
                instance,
                transform.transpose().toFloatArray()
            )
        }

        private fun updateCamera() {
            // Smooth sensor response so the model does not jump.
            smoothPitch += (sensorPitch - smoothPitch) * 0.08f
            smoothRoll += (sensorRoll - smoothRoll) * 0.08f

            val x = smoothRoll * 0.55
            val y = -smoothPitch * 0.35

            camera.lookAt(
                x.toDouble(), y.toDouble(), 7.0,
                0.0, 0.0, -4.0,
                0.0, 1.0, 0.0
            )
        }

        private fun renderFrame(frameTimeNanos: Long) {
            if (!uiHelper.isReadyToRender || swapChain == null) return

            updateCamera()

            model?.instance?.animator?.let { animator ->
                if (animator.animationCount > 0) {
                    val seconds =
                        (frameTimeNanos - startTime).toDouble() / 1_000_000_000.0
                    animator.applyAnimation(0, seconds.toFloat())
                    animator.updateBoneMatrices()
                }
            }

            if (renderer.beginFrame(swapChain!!, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            visible = isVisible

            if (isVisible) {
                rotationSensor?.let {
                    sensorManager.registerListener(
                        this,
                        it,
                        SensorManager.SENSOR_DELAY_GAME
                    )
                }
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                Choreographer.getInstance().postFrameCallback(frameCallback)
            } else {
                sensorManager.unregisterListener(this)
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            sensorRoll = orientation[2].coerceIn(-0.8f, 0.8f)
            sensorPitch = orientation[1].coerceIn(-0.8f, 0.8f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onDestroy() {
            visible = false

            Choreographer.getInstance().removeFrameCallback(frameCallback)
            sensorManager.unregisterListener(this)

            uiHelper.detach()
            swapChain?.let {
                filament.destroySwapChain(it)
                swapChain = null
            }

            model?.let { asset ->
                scene.removeEntities(*asset.entities)
                assetLoader?.destroyAsset(asset)
            }

            resourceLoader = null
            assetLoader = null
            materialProvider?.destroyMaterials()
            materialProvider = null

            filament.destroyRenderer(renderer)
            filament.destroyView(view)
            filament.destroyScene(scene)
            filament.destroyCameraComponent(camera.entity)
            EntityManager.get().destroy(camera.entity)
            filament.destroy()

            super.onDestroy()
        }

        private inner class SurfaceCallback : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { filament.destroySwapChain(it) }
                swapChain = filament.createSwapChain(surface)

                val display = if (Build.VERSION.SDK_INT >= 30) {
                    displayContext?.display
                } else {
                    @Suppress("DEPRECATION")
                    (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
                }

                display?.let { displayHelper.attach(renderer, it) }
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()

                swapChain?.let {
                    filament.destroySwapChain(it)
                    filament.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                val aspect = width.toDouble() / height.toDouble()
                camera.setProjection(
                    45.0,
                    aspect,
                    0.1,
                    50.0,
                    Camera.Fov.VERTICAL
                )
                view.viewport = Viewport(0, 0, width, height)
            }
        }
    }
}
