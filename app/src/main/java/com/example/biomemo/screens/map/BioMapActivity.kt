package com.example.biomemo.screens.map

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.biomemo.R
import com.example.biomemo.features.records.domain.BioRecordUseCases
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.screens.bio.BioRecordDetailActivity
import com.example.biomemo.ui.BioImageLoader
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BioMapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private val bioRecordUseCases = BioRecordUseCases()
    private val bioScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentPins: List<BioMapPin> = emptyList()
    private val activeMarkers = mutableListOf<Marker>()
    private val thumbnailBitmaps = mutableMapOf<String, Bitmap?>()
    private val loadingPhotoUrls = mutableSetOf<String>()
    private var selectedPinId: String? = null
    private var renderedZoomBucket: Int? = null
    private var hasLoadedInitialMapState = false
    private val focusEntryId: String?
        get() = intent.getStringExtra(EXTRA_FOCUS_ENTRY_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_bio_map)

        MainBottomNav.setup(this, MainNavDestination.RECORDS, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))

        mapView = findViewById(R.id.mapviewBioMap)
        observeMapState()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        bioScope.cancel()
        super.onDestroy()
    }

    private fun setupMap(state: BioMapUiState) {
        mapView.setTileSource(cartoLightTileSource())
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 4.0
        mapView.maxZoomLevel = 18.0
        mapView.isTilesScaledToDpi = true
        mapView.overlayManager.tilesOverlay.setColorFilter(null)
        mapView.zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                renderMapPins()
                return false
            }
        })

        val targetPin = state.focusedPin() ?: state.pins.firstOrNull()
        currentPins = state.pins
        preloadMapThumbnails()
        renderMapPins(force = true)

        if (targetPin != null) {
            selectedPinId = targetPin.id
            mapView.post {
                mapView.controller.setZoom(16.0)
                mapView.controller.setCenter(GeoPoint(targetPin.latitude, targetPin.longitude))
                renderMapPins(force = true)
            }
            return
        }

        if (state.pins.isEmpty()) {
            mapView.controller.setZoom(5.0)
            mapView.controller.setCenter(GeoPoint(44.0, -120.5))
            return
        }

    }

    private fun observeMapState() {
        bioScope.launch {
            bioRecordUseCases.observeRecords().collectLatest { entries ->
                val state = BioMapModel.fromEntries(entries)
                if (!hasLoadedInitialMapState) {
                    setupMap(state)
                    hasLoadedInitialMapState = true
                } else {
                    updateMapState(state)
                }
                setupChrome(state)
            }
        }
    }

    private fun updateMapState(state: BioMapUiState) {
        currentPins = state.pins
        selectedPinId = selectedPinId?.takeIf { selectedId -> state.pins.any { it.id == selectedId } }
        renderedZoomBucket = null
        preloadMapThumbnails()
        renderMapPins(force = true)
    }

    private fun renderMapPins(force: Boolean = false) {
        if (!::mapView.isInitialized) return
        val zoomBucket = markerZoomBucket(mapView.zoomLevelDouble)
        if (!force && renderedZoomBucket == zoomBucket) return
        renderedZoomBucket = zoomBucket
        InfoWindow.closeAllInfoWindowsOn(mapView)
        activeMarkers.forEach { marker ->
            marker.onDetach(mapView)
            mapView.overlays.remove(marker)
        }
        activeMarkers.clear()

        if (currentPins.isEmpty()) {
            mapView.invalidate()
            return
        }

        buildClusters(currentPins).forEach { cluster ->
            if (cluster.pins.size == 1) {
                addBioRecordMarker(cluster.pins.first())
            } else {
                addClusterMarker(cluster)
            }
        }

        mapView.invalidate()
    }

    private fun setupChrome(state: BioMapUiState) {
        findViewById<ImageButton>(R.id.imagebuttonBioMapBack).setOnClickListener { finish() }
        if (state.pins.isEmpty()) {
            showEmptyMessage(state)
        } else {
            findViewById<TextView>(R.id.textviewBioMapEmpty).visibility = View.GONE
        }
    }

    private fun addBioRecordMarker(pin: BioMapPin): Marker {
        val marker = Marker(mapView).apply {
            position = GeoPoint(pin.latitude, pin.longitude)
            title = pin.commonName
            subDescription = "${pin.scientificName} · ${pin.locationMetadata}"
            icon = createPhotoPinDrawable(pin)
            infoWindow = null
            relatedObject = pin.id
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { selectedMarker, _ ->
                selectedPinId = pin.id
                mapView.controller.animateTo(selectedMarker.position)
                if (mapView.zoomLevelDouble < INFO_WINDOW_MIN_ZOOM) {
                    mapView.controller.setZoom(INFO_WINDOW_MIN_ZOOM)
                } else {
                    openBioRecord(pin)
                }
                true
            }
        }
        mapView.overlays.add(marker)
        activeMarkers.add(marker)
        loadMarkerThumbnail(pin)
        return marker
    }

    private fun addClusterMarker(cluster: BioMapCluster): Marker {
        val marker = Marker(mapView).apply {
            position = GeoPoint(cluster.latitude, cluster.longitude)
            title = "${cluster.pins.size} BioRecords"
            icon = createClusterDrawable(cluster.pins.size)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { selectedMarker, _ ->
                selectedPinId = null
                InfoWindow.closeAllInfoWindowsOn(mapView)
                mapView.controller.animateTo(selectedMarker.position)
                mapView.controller.setZoom((mapView.zoomLevelDouble + CLUSTER_ZOOM_STEP).coerceAtMost(mapView.maxZoomLevel))
                true
            }
        }
        mapView.overlays.add(marker)
        activeMarkers.add(marker)
        return marker
    }

    private fun showEmptyMessage(state: BioMapUiState) {
        findViewById<TextView>(R.id.textviewBioMapEmpty).apply {
            visibility = View.VISIBLE
            text = "${state.emptyTitle.orEmpty()}\n${state.emptyMessage.orEmpty()}\n" +
            "${state.totalRecords} total · ${state.recordsWithoutLocation} missing GPS"
        }
    }

    private fun openBioRecord(pin: BioMapPin) {
        startActivity(
            Intent(this, BioRecordDetailActivity::class.java)
                .putExtra(BioRecordDetailActivity.EXTRA_ENTRY_ID, pin.id)
        )
    }

    private fun preloadMapThumbnails() {
        currentPins
            .map { it.photoUrl.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { photoUrl -> loadMarkerThumbnail(photoUrl) }
    }

    private fun loadMarkerThumbnail(pin: BioMapPin) {
        loadMarkerThumbnail(pin.photoUrl)
    }

    private fun loadMarkerThumbnail(photoUrl: String) {
        val trimmedUrl = photoUrl.trim()
        if (trimmedUrl.isBlank() || thumbnailBitmaps.containsKey(trimmedUrl) || trimmedUrl in loadingPhotoUrls) return
        loadingPhotoUrls.add(trimmedUrl)
        bioScope.launch {
            val bitmap = BioImageLoader.loadBitmap(
                photoRef = trimmedUrl,
                targetWidthPx = dp(MARKER_IMAGE_LOAD_DP),
                targetHeightPx = dp(MARKER_IMAGE_LOAD_DP),
                signedUrlResolver = { path -> bioRecordUseCases.createSignedPhotoUrl(path) }
            )
            loadingPhotoUrls.remove(trimmedUrl)
            thumbnailBitmaps[trimmedUrl] = bitmap
            refreshVisiblePhotoMarkers(trimmedUrl)
        }
    }

    private fun refreshVisiblePhotoMarkers(photoUrl: String) {
        activeMarkers.forEach { marker ->
            val pinId = marker.relatedObject as? String ?: return@forEach
            val pin = currentPins.firstOrNull { it.id == pinId && it.photoUrl == photoUrl } ?: return@forEach
            marker.icon = createPhotoPinDrawable(pin)
        }
        mapView.invalidate()
    }

    private fun buildClusters(pins: List<BioMapPin>): List<BioMapCluster> {
        val zoom = mapView.zoomLevelDouble
        if (zoom >= CLUSTER_DISABLE_ZOOM) return BioMapClusterer.buildSinglePinClusters(pins)
        val radiusPx = dp(clusterRadiusDp(zoom))
        val projection = mapView.projection
        val projectedPins = pins.map { pin ->
            val point = projection.toPixels(GeoPoint(pin.latitude, pin.longitude), Point())
            BioMapProjectedPin(pin = pin, x = point.x, y = point.y)
        }
        return BioMapClusterer.buildClusters(projectedPins, radiusPx)
    }

    private fun createClusterDrawable(count: Int): BitmapDrawable {
        val size = dp(clusterSizeDp(mapView.zoomLevelDouble))
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f
        val radius = center - dp(3)

        paint.color = Color.argb(95, 18, 34, 28)
        canvas.drawCircle(center, center + dp(2), radius, paint)
        paint.color = getColor(R.color.bio_forest_700)
        canvas.drawCircle(center, center, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(3).toFloat()
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, radius - dp(1), paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.34f
        val label = if (count > 99) "99+" else count.toString()
        val textY = center - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(label, center, textY, paint)
        return BitmapDrawable(resources, bitmap)
    }

    private fun createPhotoPinDrawable(pin: BioMapPin): BitmapDrawable {
        val markerSize = dp(photoMarkerSizeDp(mapView.zoomLevelDouble))
        val shadowOffset = dp(2).toFloat()
        val borderWidth = dp(2).toFloat()
        val cornerRadius = markerSize * 0.18f
        val bitmap = Bitmap.createBitmap(markerSize, markerSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val shadowBounds = RectF(borderWidth, borderWidth + shadowOffset, markerSize - borderWidth, markerSize - borderWidth + shadowOffset)
        paint.color = Color.argb(70, 18, 34, 28)
        canvas.drawRoundRect(shadowBounds, cornerRadius, cornerRadius, paint)

        val borderBounds = RectF(borderWidth, borderWidth, markerSize - borderWidth, markerSize - borderWidth)
        paint.color = Color.WHITE
        canvas.drawRoundRect(borderBounds, cornerRadius, cornerRadius, paint)

        val photoBounds = RectF(
            borderBounds.left + borderWidth,
            borderBounds.top + borderWidth,
            borderBounds.right - borderWidth,
            borderBounds.bottom - borderWidth
        )
        val photoRadius = (cornerRadius - borderWidth).coerceAtLeast(0f)
        val photoPath = Path().apply { addRoundRect(photoBounds, photoRadius, photoRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(photoPath)
        val markerBitmap = thumbnailBitmaps[pin.photoUrl.trim()]
        if (markerBitmap != null) {
            canvas.drawBitmap(markerBitmap, null, photoBounds, paint)
        } else {
            paint.color = getColor(R.color.bio_mint_100)
            canvas.drawRect(photoBounds, paint)
            ContextCompat.getDrawable(this, R.drawable.ic_bio_record_photo)?.let { drawable ->
                val iconInset = (markerSize * 0.30f).toInt()
                drawable.setTint(getColor(R.color.bio_forest_700))
                drawable.bounds = Rect(
                    (photoBounds.left + iconInset).toInt(),
                    (photoBounds.top + iconInset).toInt(),
                    (photoBounds.right - iconInset).toInt(),
                    (photoBounds.bottom - iconInset).toInt()
                )
                drawable.draw(canvas)
            }
        }
        canvas.restore()
        return BitmapDrawable(resources, bitmap)
    }

    private fun markerZoomBucket(zoom: Double): Int = when {
        zoom >= 16.0 -> 6
        zoom >= 15.0 -> 5
        zoom >= 14.0 -> 4
        zoom >= 12.0 -> 3
        zoom >= 10.0 -> 2
        zoom >= 8.0 -> 1
        else -> 0
    }

    private fun photoMarkerSizeDp(zoom: Double): Int = when {
        zoom >= 16.0 -> 42
        zoom >= 14.0 -> 38
        zoom >= 12.0 -> 34
        zoom >= 10.0 -> 30
        else -> 26
    }

    private fun clusterSizeDp(zoom: Double): Int = when {
        zoom >= 12.0 -> 52
        zoom >= 10.0 -> 46
        else -> 40
    }

    private fun clusterRadiusDp(zoom: Double): Int = when {
        zoom < 8.0 -> 112
        zoom < 10.0 -> 96
        zoom < 12.0 -> 78
        else -> 62
    }

    private fun cartoLightTileSource(): XYTileSource = XYTileSource(
        "CartoLight",
        1,
        20,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/light_all/",
            "https://b.basemaps.cartocdn.com/light_all/",
            "https://c.basemaps.cartocdn.com/light_all/"
        ),
        "© OpenStreetMap contributors, © CARTO"
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun BioMapUiState.focusedPin(): BioMapPin? {
        val requestedId = focusEntryId ?: return null
        return pins.firstOrNull { it.id == requestedId }
    }

    companion object {
        const val EXTRA_FOCUS_ENTRY_ID = "focus_bio_record_id"
        private const val CLUSTER_DISABLE_ZOOM = 15.0
        private const val CLUSTER_ZOOM_STEP = 2.0
        private const val INFO_WINDOW_MIN_ZOOM = 13.0
        private const val MARKER_IMAGE_LOAD_DP = 96
    }
}
