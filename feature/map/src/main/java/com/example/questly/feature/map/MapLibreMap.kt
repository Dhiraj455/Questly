package com.example.questly.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.questly.core.location.UserLocation
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

private const val MARKER_ICON = "questly-marker"

// OpenFreeMap: free, no API key, no signup, meant for production app use.
// (OSM's own tile servers 403-block app traffic, so they can't be used directly.)
// Vector tiles — MapLibre's native strength. Alternatives: ".../styles/positron", ".../styles/bright".
private const val OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/**
 * MapLibre map showing an OSM basemap with a marker per checkpoint. Tapping a
 * marker invokes [onMarkerClick] with that checkpoint's id. Camera centers on the
 * first user location fix.
 */
@Composable
fun MapLibreMap(
    checkpoints: List<CheckpointUi>,
    userLocation: UserLocation?,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapViewWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    val symbolToCheckpoint = remember { mutableMapOf<Long, String>() }
    var didCenter by remember { mutableStateOf(false) }
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)

    // One-time: acquire the map, load the OSM style, create the symbol manager.
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.setStyle(Style.Builder().fromUri(OPENFREEMAP_STYLE)) { style ->
                style.addImage(MARKER_ICON, markerBitmap())
                symbolManager = SymbolManager(mapView, m, style).apply {
                    iconAllowOverlap = true
                    iconIgnorePlacement = true
                    addClickListener { symbol ->
                        symbolToCheckpoint[symbol.id]?.let(currentOnMarkerClick)
                        true
                    }
                }
            }
        }
    }

    // Rebuild markers whenever the checkpoint set (or the manager) changes.
    LaunchedEffect(symbolManager, checkpoints) {
        val mgr = symbolManager ?: return@LaunchedEffect
        mgr.deleteAll()
        symbolToCheckpoint.clear()
        checkpoints.forEach { item ->
            val symbol = mgr.create(
                SymbolOptions()
                    .withLatLng(LatLng(item.checkpoint.lat, item.checkpoint.lng))
                    .withIconImage(MARKER_ICON)
                    .withIconSize(1.2f),
            )
            symbolToCheckpoint[symbol.id] = item.checkpoint.id
        }
    }

    // Center on the first location fix, then let the user pan freely.
    LaunchedEffect(map, userLocation) {
        val m = map ?: return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect
        if (!didCenter) {
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(loc.lat, loc.lng))
                .zoom(14.0)
                .build()
            didCenter = true
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun markerBitmap(): Bitmap {
    val size = 48
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = size / 2f
    canvas.drawCircle(r, r, r - 4, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D32F2F") })
    canvas.drawCircle(r, r, r - 4, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    })
    return bmp
}
