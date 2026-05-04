package com.example.biomemo.screens.map

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.biomemo.R
import com.example.biomemo.data.BioEntry
import com.example.biomemo.data.BioRepository
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class BioMapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private val repository = BioRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_bio_map)

        val entries = repository.getAllEntries().filter { it.latitude != null && it.longitude != null }
        mapView = findViewById(R.id.mapviewBioMap)
        setupMap(entries)
        setupChrome(entries)
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    private fun setupMap(entries: List<BioEntry>) {
        mapView.setTileSource(cartoLightTileSource())
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 4.0
        mapView.maxZoomLevel = 18.0
        mapView.isTilesScaledToDpi = true
        mapView.overlayManager.tilesOverlay.setColorFilter(null)
        mapView.zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

        entries.forEach { addBioRecordMarker(it) }

        if (entries.isEmpty()) {
            mapView.controller.setZoom(5.0)
            mapView.controller.setCenter(GeoPoint(44.0, -120.5))
            return
        }

        mapView.post {
            val latitudes = entries.mapNotNull { it.latitude }
            val longitudes = entries.mapNotNull { it.longitude }
            val box = BoundingBox(
                latitudes.maxOrNull() ?: 49.0,
                longitudes.maxOrNull() ?: -116.0,
                latitudes.minOrNull() ?: 38.0,
                longitudes.minOrNull() ?: -124.0
            )
            mapView.zoomToBoundingBox(box.increaseByScale(1.35f), true, dp(72))
        }
    }

    private fun setupChrome(entries: List<BioEntry>) {
        findViewById<TextView>(R.id.textviewBioMapBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.textviewBioMapSummary).text =
            "${entries.size} BioRecord pins · minimalist OSM view"
    }

    private fun addBioRecordMarker(entry: BioEntry) {
        val marker = Marker(mapView).apply {
            position = GeoPoint(entry.latitude ?: return, entry.longitude ?: return)
            title = entry.commonName
            subDescription = "${entry.scientificName} · ${entry.location}"
            icon = ContextCompat.getDrawable(this@BioMapActivity, R.drawable.ic_bio_map_pin)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { selectedMarker, _ ->
                selectedMarker.showInfoWindow()
                showBioRecordPreview(entry)
                true
            }
        }
        mapView.overlays.add(marker)
    }

    private fun showBioRecordPreview(entry: BioEntry) {
        findViewById<LinearLayout>(R.id.linearlayoutBioMapPreview).visibility = View.VISIBLE
        findViewById<TextView>(R.id.textviewBioMapPreviewTitle).text = entry.commonName
        findViewById<TextView>(R.id.textviewBioMapPreviewSubtitle).text =
            "${entry.scientificName} · ${entry.category}"
        findViewById<TextView>(R.id.textviewBioMapPreviewMeta).text =
            "${entry.location} · ${entry.date} · ${entry.confidence}% ID confidence"
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
}
