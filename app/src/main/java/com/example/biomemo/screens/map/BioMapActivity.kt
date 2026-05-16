package com.example.biomemo.screens.map

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.data.BioRepository
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BioMapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private val repository = BioRepository()
    private val bioScope = CoroutineScope(Dispatchers.Main + Job())
    private val focusEntryId: String?
        get() = intent.getStringExtra(EXTRA_FOCUS_ENTRY_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_bio_map)

        MainBottomNav.setup(this, MainNavDestination.RECORDS, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))

        mapView = findViewById(R.id.mapviewBioMap)
        bioScope.launch {
            val state = BioMapModel.fromEntries(repository.getAllEntries())
            setupMap(state)
            setupChrome(state)
        }
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

        state.pins.forEach { addBioRecordMarker(it) }

        val focusedPin = state.focusedPin()
        if (focusedPin != null) {
            mapView.post {
                mapView.controller.setZoom(16.0)
                mapView.controller.setCenter(GeoPoint(focusedPin.latitude, focusedPin.longitude))
            }
            return
        }

        if (state.pins.isEmpty()) {
            mapView.controller.setZoom(5.0)
            mapView.controller.setCenter(GeoPoint(44.0, -120.5))
            return
        }

        mapView.post {
            val latitudes = state.pins.map { it.latitude }
            val longitudes = state.pins.map { it.longitude }
            val box = BoundingBox(
                latitudes.maxOrNull() ?: 49.0,
                longitudes.maxOrNull() ?: -116.0,
                latitudes.minOrNull() ?: 38.0,
                longitudes.minOrNull() ?: -124.0
            )
            mapView.zoomToBoundingBox(box.increaseByScale(1.35f), true, dp(72))
        }
    }

    private fun setupChrome(state: BioMapUiState) {
        findViewById<TextView>(R.id.textviewBioMapBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.textviewBioMapSummary).text = state.summary
        if (state.pins.isEmpty()) {
            showEmptyPreview(state)
        } else {
            showBioRecordPreview(state.focusedPin() ?: state.pins.first())
        }
    }

    private fun addBioRecordMarker(pin: BioMapPin) {
        val marker = Marker(mapView).apply {
            position = GeoPoint(pin.latitude, pin.longitude)
            title = pin.commonName
            subDescription = "${pin.scientificName} · ${pin.locationMetadata}"
            icon = ContextCompat.getDrawable(this@BioMapActivity, R.drawable.ic_bio_map_pin)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { selectedMarker, _ ->
                selectedMarker.showInfoWindow()
                showBioRecordPreview(pin)
                true
            }
        }
        mapView.overlays.add(marker)
    }

    private fun showBioRecordPreview(pin: BioMapPin) {
        findViewById<LinearLayout>(R.id.linearlayoutBioMapPreview).visibility = View.VISIBLE
        findViewById<TextView>(R.id.textviewBioMapPreviewTitle).text = pin.commonName
        findViewById<TextView>(R.id.textviewBioMapPreviewSubtitle).text =
            "${pin.scientificName} · ${pin.category}"
        findViewById<TextView>(R.id.textviewBioMapPreviewMeta).text =
            "${pin.primaryMetadata}\n${pin.locationMetadata}\n${pin.tagsLabel}"
    }

    private fun showEmptyPreview(state: BioMapUiState) {
        findViewById<LinearLayout>(R.id.linearlayoutBioMapPreview).visibility = View.VISIBLE
        findViewById<TextView>(R.id.textviewBioMapPreviewTitle).text = state.emptyTitle.orEmpty()
        findViewById<TextView>(R.id.textviewBioMapPreviewSubtitle).text = state.emptyMessage.orEmpty()
        findViewById<TextView>(R.id.textviewBioMapPreviewMeta).text =
            "${state.totalRecords} total · ${state.recordsWithoutLocation} missing GPS"
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
    }
}
