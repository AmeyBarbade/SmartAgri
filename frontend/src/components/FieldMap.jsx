import React, { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { MapPin, Navigation, Trash2, CheckCircle2, Crosshair, Layers } from 'lucide-react'
import { Button, Notice, Panel, Stat } from './ui'
import { fmt, fmtFixed } from '../format'

// Standard geodesic area calculation on WGS-84 ellipsoid / sphere
function calculateGeodesicAreaHa(coords) {
  if (!coords || coords.length < 3) return 0
  const R = 6378137 // Earth radius in meters
  let total = 0
  const n = coords.length

  for (let i = 0; i < n; i++) {
    const prev = coords[(i - 1 + n) % n]
    const next = coords[(i + 1) % n]
    const curr = coords[i]

    const prevLonRad = (prev[1] * Math.PI) / 180
    const nextLonRad = (next[1] * Math.PI) / 180
    const currLatRad = (curr[0] * Math.PI) / 180

    total += (nextLonRad - prevLonRad) * Math.sin(currLatRad)
  }

  const areaSqM = Math.abs((total * R * R) / 2.0)
  return Math.round((areaSqM / 10000.0) * 1000) / 1000
}

function calculateCentroid(coords) {
  if (!coords || coords.length === 0) return null
  let sumLat = 0
  let sumLon = 0
  for (const c of coords) {
    sumLat += c[0]
    sumLon += c[1]
  }
  return {
    lat: Math.round((sumLat / coords.length) * 1000000) / 1000000,
    lon: Math.round((sumLon / coords.length) * 1000000) / 1000000,
  }
}

const PRESETS = [
  { name: 'Patna (Bihar Rice)', lat: 25.5941, lon: 85.1376 },
  { name: 'Ludhiana (Punjab Wheat)', lat: 30.9010, lon: 75.8573 },
  { name: 'Akola (Vidarbha Black Soil)', lat: 20.7002, lon: 77.0082 },
  { name: 'Guntur (Andhra Delta)', lat: 16.3067, lon: 80.4365 },
]

export default function FieldMap({ field, farm, onSaveBoundary }) {
  const mapContainerRef = useRef(null)
  const mapInstanceRef = useRef(null)
  const drawLayerGroupRef = useRef(null)
  const polygonLayerRef = useRef(null)

  const [isDrawing, setIsDrawing] = useState(false)
  const [points, setPoints] = useState([])
  const [satelliteMode, setSatelliteMode] = useState(true)
  const [saving, setSaving] = useState(false)

  // Initial coordinates priority: field centroid -> farm coords -> Patna default
  const initialLat = field.centroidLat ?? farm?.latitude ?? 25.5941
  const initialLon = field.centroidLon ?? farm?.longitude ?? 85.1376

  // Existing polygon points from field.boundaryGeojson
  const existingCoords = (() => {
    if (!field.boundaryGeojson) return null
    try {
      const parsed = JSON.parse(field.boundaryGeojson)
      const ring = parsed.coordinates ? parsed.coordinates[0] : parsed
      if (Array.isArray(ring) && ring.length >= 3) {
        // GeoJSON is [lon, lat], Leaflet expects [lat, lon]
        return ring.map((pt) => [pt[1], pt[0]])
      }
    } catch {
      return null
    }
    return null
  })()

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current) return

    // Leaflet icon fix for bundlers
    delete L.Icon.Default.prototype._getIconUrl
    L.Icon.Default.mergeOptions({
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
    })

    const map = L.map(mapContainerRef.current, {
      center: [initialLat, initialLon],
      zoom: existingCoords ? 16 : 14,
      scrollWheelZoom: true,
    })
    mapInstanceRef.current = map

    // Tile Layers
    const esriSat = L.tileLayer(
      'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      {
        attribution: 'Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community',
        maxZoom: 19,
      }
    )

    const osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    })

    if (satelliteMode) {
      esriSat.addTo(map)
    } else {
      osm.addTo(map)
    }

    const drawGroup = L.layerGroup().addTo(map)
    drawLayerGroupRef.current = drawGroup

    // Render existing polygon if present
    if (existingCoords && existingCoords.length >= 3) {
      const poly = L.polygon(existingCoords, {
        color: '#10b981',
        weight: 3,
        fillColor: '#10b981',
        fillOpacity: 0.25,
      }).addTo(map)
      polygonLayerRef.current = poly
      map.fitBounds(poly.getBounds(), { padding: [30, 30] })

      const centroid = calculateCentroid(existingCoords)
      if (centroid) {
        L.circleMarker([centroid.lat, centroid.lon], {
          radius: 6,
          color: '#047857',
          fillColor: '#34d399',
          fillOpacity: 1,
        })
          .addTo(drawGroup)
          .bindPopup(`<b>${field.name} Centroid</b><br>${centroid.lat}, ${centroid.lon}`)
      }
    }

    setTimeout(() => {
      map.invalidateSize()
    }, 200)

    return () => {
      map.remove()
      mapInstanceRef.current = null
    }
  }, [])

  // Switch Base Layer
  const toggleLayer = () => {
    const map = mapInstanceRef.current
    if (!map) return

    map.eachLayer((layer) => {
      if (layer instanceof L.TileLayer) {
        map.removeLayer(layer)
      }
    })

    const nextMode = !satelliteMode
    setSatelliteMode(nextMode)

    if (nextMode) {
      L.tileLayer(
        'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        { maxZoom: 19 }
      ).addTo(map)
    } else {
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map)
    }
  }

  // Handle map clicks when drawing
  useEffect(() => {
    const map = mapInstanceRef.current
    if (!map) return

    const handleClick = (e) => {
      if (!isDrawing) return
      const newPt = [e.latlng.lat, e.latlng.lng]
      setPoints((prev) => [...prev, newPt])
    }

    map.on('click', handleClick)
    return () => {
      map.off('click', handleClick)
    }
  }, [isDrawing])

  // Update visual markers and polygon during drawing
  useEffect(() => {
    const drawGroup = drawLayerGroupRef.current
    if (!drawGroup) return

    // Don't wipe existing saved polygon unless we have new active points
    if (points.length === 0) {
      if (isDrawing) {
        drawGroup.clearLayers()
      }
      return
    }

    drawGroup.clearLayers()

    // Draw vertex markers
    points.forEach((pt, idx) => {
      L.circleMarker(pt, {
        radius: 5,
        color: '#f59e0b',
        fillColor: '#fbbf24',
        fillOpacity: 1,
      })
        .addTo(drawGroup)
        .bindTooltip(`Vertex ${idx + 1}`, { permanent: false })
    })

    // Draw lines / polygon
    if (points.length >= 3) {
      L.polygon(points, {
        color: '#f59e0b',
        weight: 2,
        fillColor: '#fbbf24',
        fillOpacity: 0.3,
      }).addTo(drawGroup)
    } else if (points.length === 2) {
      L.polyline(points, {
        color: '#f59e0b',
        weight: 2,
        dashArray: '4, 4',
      }).addTo(drawGroup)
    }
  }, [points, isDrawing])

  const calculatedAreaHa = points.length >= 3 ? calculateGeodesicAreaHa(points) : (existingCoords ? calculateGeodesicAreaHa(existingCoords) : null)
  const calculatedAcres = calculatedAreaHa != null ? Math.round(calculatedAreaHa * 2.47105 * 100) / 100 : null
  const activeCentroid = points.length >= 3 ? calculateCentroid(points) : (existingCoords ? calculateCentroid(existingCoords) : null)

  const handleStartDrawing = () => {
    if (polygonLayerRef.current) {
      polygonLayerRef.current.remove()
      polygonLayerRef.current = null
    }
    drawLayerGroupRef.current?.clearLayers()
    setPoints([])
    setIsDrawing(true)
  }

  const handleClear = () => {
    drawLayerGroupRef.current?.clearLayers()
    if (polygonLayerRef.current) {
      polygonLayerRef.current.remove()
      polygonLayerRef.current = null
    }
    setPoints([])
    setIsDrawing(false)
  }

  const handleSave = async () => {
    if (points.length < 3) return
    try {
      setSaving(true)
      const areaHa = calculateGeodesicAreaHa(points)
      const centroid = calculateCentroid(points)

      // GeoJSON standard: [lon, lat], closed ring (first pt = last pt)
      const geojsonRing = [...points, points[0]].map((p) => [
        Math.round(p[1] * 1000000) / 1000000,
        Math.round(p[0] * 1000000) / 1000000,
      ])
      const geojson = {
        type: 'Polygon',
        coordinates: [geojsonRing],
      }

      await onSaveBoundary({
        boundaryGeojson: JSON.stringify(geojson),
        centroidLat: centroid.lat,
        centroidLon: centroid.lon,
        areaHa: areaHa > 0 ? areaHa : field.areaHa,
      })
      setIsDrawing(false)
    } catch (err) {
      console.error('Failed to save boundary:', err)
      alert('Error saving boundary: ' + (err.message || err))
    } finally {
      setSaving(false)
    }
  }

  const jumpTo = (lat, lon) => {
    const map = mapInstanceRef.current
    if (map) {
      map.flyTo([lat, lon], 16, { duration: 1.5 })
    }
  }

  return (
    <Panel className="overflow-hidden mb-6">
      {/* Map Control Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line bg-canvas/40 px-5 py-3">
        <div className="flex items-center gap-2">
          <MapPin className="size-4 text-accent" />
          <span className="text-sm font-semibold text-ink">GIS Field Satellite Boundary</span>
          {existingCoords && !isDrawing && (
            <span className="rounded bg-accent-soft px-2 py-0.5 text-xs font-medium text-accent">
              ✓ Boundary Defined
            </span>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={toggleLayer}
            title="Toggle between Satellite Imagery and Street Map"
          >
            <Layers className="size-3.5" />
            {satelliteMode ? 'Satellite (Esri)' : 'OpenStreetMap'}
          </Button>

          {!isDrawing ? (
            <button
              type="button"
              onClick={handleStartDrawing}
              className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-700 px-3.5 py-1.5 text-xs font-bold text-white shadow-sm hover:bg-emerald-800 transition-colors"
            >
              <Crosshair className="size-3.5" />
              {existingCoords ? 'Trace Farm Boundary (Redraw)' : 'Trace Farm Boundary'}
            </button>
          ) : (
            <>
              <Button variant="secondary" size="sm" onClick={handleClear}>
                <Trash2 className="size-3.5" />
                Clear
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={handleSave}
                disabled={points.length < 3 || saving}
                loading={saving}
              >
                <CheckCircle2 className="size-3.5" />
                Save Boundary & Acreage
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Preset Quick Navigation Bar */}
      <div className="flex flex-wrap items-center gap-2 border-b border-line bg-surface px-5 py-2 text-xs text-muted">
        <span className="font-medium text-ink-2">Quick Jump:</span>
        {PRESETS.map((p) => (
          <button
            key={p.name}
            type="button"
            onClick={() => jumpTo(p.lat, p.lon)}
            className="rounded border border-line px-2 py-1 text-[11px] hover:bg-canvas hover:text-ink transition-colors"
          >
            {p.name}
          </button>
        ))}
      </div>

      {/* Leaflet Map Canvas */}
      <div className="relative">
        <div ref={mapContainerRef} className="h-80 w-full z-0" />

        {isDrawing && (
          <div className="absolute top-3 left-3 z-[1000] rounded-md bg-surface/90 backdrop-blur border border-line px-3 py-2 shadow-md text-xs text-ink max-w-xs">
            <p className="font-semibold text-accent">Drawing Mode Active</p>
            <p className="text-muted mt-0.5">Click corners of the field to outline vertices ({points.length} points placed).</p>
            {points.length < 3 && <p className="text-warn mt-1 font-medium">Place at least 3 points to enclose polygon.</p>}
          </div>
        )}
      </div>

      {/* Boundary Metrics Strip */}
      <div className="grid grid-cols-2 divide-x divide-line border-t border-line bg-surface sm:grid-cols-4">
        <div className="px-5 py-3">
          <Stat label="Calculated Area">
            {calculatedAreaHa != null ? `${fmt(calculatedAreaHa, 3)} ha` : '—'}
          </Stat>
        </div>
        <div className="px-5 py-3">
          <Stat label="Imperial Acreage">
            {calculatedAcres != null ? `${calculatedAcres} acres` : '—'}
          </Stat>
        </div>
        <div className="px-5 py-3">
          <Stat label="Centroid Latitude">
            {activeCentroid ? `${activeCentroid.lat}°` : (field.centroidLat ? `${field.centroidLat}°` : '—')}
          </Stat>
        </div>
        <div className="px-5 py-3">
          <Stat label="Centroid Longitude">
            {activeCentroid ? `${activeCentroid.lon}°` : (field.centroidLon ? `${field.centroidLon}°` : '—')}
          </Stat>
        </div>
      </div>
    </Panel>
  )
}
