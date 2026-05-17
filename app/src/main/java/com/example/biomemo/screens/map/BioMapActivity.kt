package com.example.biomemo.screens.map

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.biomemo.R
import com.example.biomemo.data.BioRepository
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.screens.bio.BioRecordDetailActivity
import com.example.biomemo.ui.BioImageLoader
import com.example.biomemo.ui.applyRoundedCorners
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
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

        val targetPin = state.focusedPin() ?: state.pins.firstOrNull()
        var targetMarker: Marker? = null
        state.pins.forEach { pin ->
            val marker = addBioRecordMarker(pin)
            if (pin.id == targetPin?.id) targetMarker = marker
        }

        if (targetPin != null) {
            mapView.post {
                mapView.controller.setZoom(16.0)
                mapView.controller.setCenter(GeoPoint(targetPin.latitude, targetPin.longitude))
                targetMarker?.showInfoWindow()
            }
            return
        }

        if (state.pins.isEmpty()) {
            mapView.controller.setZoom(5.0)
            mapView.controller.setCenter(GeoPoint(44.0, -120.5))
            return
        }

    }

    private fun setupChrome(state: BioMapUiState) {
        findViewById<ImageButton>(R.id.imagebuttonBioMapBack).setOnClickListener { finish() }
        if (state.pins.isEmpty()) {
            showEmptyMessage(state)
        }
    }

    private fun addBioRecordMarker(pin: BioMapPin): Marker {
        val marker = Marker(mapView).apply {
            position = GeoPoint(pin.latitude, pin.longitude)
            title = pin.commonName
            subDescription = "${pin.scientificName} · ${pin.locationMetadata}"
            icon = ContextCompat.getDrawable(this@BioMapActivity, R.drawable.ic_bio_map_pin)
            infoWindow = BioRecordInfoWindow(pin)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { selectedMarker, _ ->
                mapView.controller.animateTo(selectedMarker.position)
                selectedMarker.showInfoWindow()
                true
            }
        }
        mapView.overlays.add(marker)
        return marker
    }

    private fun showEmptyMessage(state: BioMapUiState) {
        findViewById<TextView>(R.id.textviewBioMapEmpty).apply {
            visibility = android.view.View.VISIBLE
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

    private fun loadPinThumbnail(pin: BioMapPin, imageView: ImageView) {
        imageView.setImageResource(R.drawable.ic_bio_record_photo)
        imageView.tag = pin.photoUrl
        if (pin.photoUrl.isBlank()) return
        bioScope.launch {
            val bitmap = BioImageLoader.loadBitmap(
                photoRef = pin.photoUrl,
                targetWidthPx = dp(64),
                targetHeightPx = dp(64),
                signedUrlResolver = { path -> repository.createSignedPhotoUrl(path) }
            )
            if (bitmap != null && imageView.tag == pin.photoUrl) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private inner class BioRecordInfoWindow(
        private val pin: BioMapPin
    ) : InfoWindow(R.layout.view_bio_map_pin_info, mapView) {
        override fun onOpen(item: Any?) {
            val thumbnail = mView.findViewById<ImageView>(R.id.imageviewBioMapPinThumbnail)
            thumbnail.applyRoundedCorners(dp(10).toFloat())
            mView.setOnClickListener { openBioRecord(pin) }
            loadPinThumbnail(pin, thumbnail)
        }

        override fun onClose() {
            mView.setOnClickListener(null)
        }
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
