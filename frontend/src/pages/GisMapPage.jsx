import React, { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { Globe, Layers, MapPin, ExternalLink, ArrowRight, ShieldCheck, Crosshair } from 'lucide-react'
import { loadOverview } from '../api/overview'
import { useAsync } from '../useAsync'
import { Button, ErrorNotice, Loading, PageHeader, Panel, Stat } from '../components/ui'
import { fmt, titleCase } from '../format'

const PRESETS = [
  { name: 'Patna (Bihar Rice)', lat: 25.5941, lon: 85.1376 },
  { name: 'Ludhiana (Punjab Wheat)', lat: 30.9010, lon: 75.8573 },
  { name: 'Akola (Vidarbha Black Soil)', lat: 20.7002, lon: 77.0082 },
  { name: 'Guntur (Andhra Delta)', lat: 16.3067, lon: 80.4365 },
]

export default function GisMapPage() {
  const { data, error, loading, reload } = useAsync(loadOverview, [])

  const mapContainerRef = useRef(null)
  const mapInstanceRef = useRef(null)
  const layerGroupRef = useRef(null)
  const [satelliteMode, setSatelliteMode] = useState(true)
  const [selectedFieldId, setSelectedFieldId] = useState(null)

  // Flatten all fields with farm context
  const allFields = (data ?? []).flatMap(({ farm, fields }) =>
    fields.map(({ field, latestSoil, latestRecommendation }) => ({
      ...field,
      farmName: farm.name,
      farmLat: farm.latitude,
      farmLon: farm.longitude,
      latestSoil,
      latestRecommendation,
    }))
  )

  const totalArea = allFields.reduce((sum, f) => sum + (Number(f.areaHa) || 0), 0)
  const mappedCount = allFields.filter((f) => f.boundaryGeojson || f.centroidLat).length

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || !data) return

    delete L.Icon.Default.prototype._getIconUrl
    L.Icon.Default.mergeOptions({
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
    })

    // Find initial center
    const firstCoord = allFields.find((f) => f.centroidLat || f.farmLat)
    const initLat = firstCoord ? (firstCoord.centroidLat ?? firstCoord.farmLat) : 25.5941
    const initLon = firstCoord ? (firstCoord.centroidLon ?? firstCoord.farmLon) : 85.1376

    const map = L.map(mapContainerRef.current, {
      center: [Number(initLat), Number(initLon)],
      zoom: 14,
      scrollWheelZoom: true,
    })
    mapInstanceRef.current = map

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

    const group = L.layerGroup().addTo(map)
    layerGroupRef.current = group

    // Plot fields & polygons
    const bounds = []
    allFields.forEach((f) => {
      const lat = Number(f.centroidLat ?? f.farmLat ?? 25.5941)
      const lon = Number(f.centroidLon ?? f.farmLon ?? 85.1376)
      bounds.push([lat, lon])

      // Render polygon if present
      if (f.boundaryGeojson) {
        try {
          const parsed = JSON.parse(f.boundaryGeojson)
          const ring = parsed.coordinates ? parsed.coordinates[0] : parsed
          if (Array.isArray(ring)) {
            const leafletCoords = ring.map((pt) => [pt[1], pt[0]])
            L.polygon(leafletCoords, {
              color: '#10b981',
              weight: 3,
              fillColor: '#10b981',
              fillOpacity: 0.3,
            })
              .addTo(group)
              .bindPopup(`
                <div style="font-family: inherit; font-size: 13px; line-height: 1.4;">
                  <b style="font-size: 14px; color: #111;">${f.name}</b><br/>
                  <span style="color: #666;">${f.farmName}</span><br/>
                  <b>Area:</b> ${fmt(f.areaHa)} ha (${Math.round(f.areaHa * 2.47105 * 100) / 100} acres)<br/>
                  <b>Crop:</b> ${f.crop ? f.crop.name : 'Not set'}<br/>
                  <div style="margin-top: 8px;">
                    <a href="/fields/${f.id}" style="color: #059669; font-weight: 600; text-decoration: underline;">Open Field Details →</a>
                  </div>
                </div>
              `)
          }
        } catch (e) {
          console.error('Error parsing polygon:', e)
        }
      }

      // Marker for field
      const marker = L.circleMarker([lat, lon], {
        radius: 7,
        color: '#047857',
        fillColor: '#34d399',
        fillOpacity: 0.9,
      }).addTo(group)

      marker.bindPopup(`
        <div style="font-family: inherit; font-size: 13px; line-height: 1.4;">
          <b style="font-size: 14px; color: #111;">${f.name}</b><br/>
          <span style="color: #666;">${f.farmName}</span><br/>
          <b>Area:</b> ${fmt(f.areaHa)} ha<br/>
          <b>Crop:</b> ${f.crop ? f.crop.name : 'Not set'}<br/>
          <div style="margin-top: 8px;">
            <a href="/fields/${f.id}" style="color: #059669; font-weight: 600; text-decoration: underline;">Open Field Details →</a>
          </div>
        </div>
      `)
    })

    if (bounds.length > 0) {
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 16 })
    }

    setTimeout(() => {
      map.invalidateSize()
    }, 200)

    return () => {
      map.remove()
      mapInstanceRef.current = null
    }
  }, [data])

  const toggleLayer = () => {
    const map = mapInstanceRef.current
    if (!map) return

    map.eachLayer((layer) => {
      if (layer instanceof L.TileLayer) {
        map.removeLayer(layer)
      }
    })

    const next = !satelliteMode
    setSatelliteMode(next)

    if (next) {
      L.tileLayer(
        'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        { maxZoom: 19 }
      ).addTo(map)
    } else {
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map)
    }
  }

  const handleSelectField = (fieldId) => {
    setSelectedFieldId(fieldId)
    const target = allFields.find((f) => f.id === Number(fieldId))
    if (!target || !mapInstanceRef.current) return

    const lat = Number(target.centroidLat ?? target.farmLat ?? 25.5941)
    const lon = Number(target.centroidLon ?? target.farmLon ?? 85.1376)
    mapInstanceRef.current.flyTo([lat, lon], 17, { duration: 1.5 })
  }

  const jumpTo = (lat, lon) => {
    if (mapInstanceRef.current) {
      mapInstanceRef.current.flyTo([lat, lon], 15, { duration: 1.5 })
    }
  }

  if (loading && !data) return <Loading label="Loading GIS field data…" />
  if (error) return <ErrorNotice error={error} onRetry={reload} />

  return (
    <>
      <PageHeader
        title="GIS Satellite Farm & Field Overview"
        meta="High-resolution satellite view of all parcels, GIS boundaries, and crop monitoring."
        actions={
          <Button variant="secondary" onClick={toggleLayer}>
            <Layers className="size-4" />
            {satelliteMode ? 'Esri Satellite' : 'OpenStreetMap'}
          </Button>
        }
      />

      {/* Stats bar */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 mb-6">
        <Panel className="p-4">
          <Stat label="Total Parcels">{allFields.length} fields</Stat>
        </Panel>
        <Panel className="p-4">
          <Stat label="Total Managed Area">{fmt(totalArea, 2)} ha</Stat>
        </Panel>
        <Panel className="p-4">
          <Stat label="Parcels with Boundary">{mappedCount} mapped</Stat>
        </Panel>
        <Panel className="p-4">
          <Stat label="Imagery Source">Esri World Imagery</Stat>
        </Panel>
      </div>

      {/* Map Control Strip */}
      <Panel className="overflow-hidden mb-6">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line bg-canvas/40 px-5 py-3">
          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold text-ink-2 uppercase tracking-wider">Focus Field:</span>
            <select
              value={selectedFieldId ?? ''}
              onChange={(e) => handleSelectField(e.target.value)}
              className="rounded-md border border-line bg-surface px-3 py-1.5 text-sm font-medium text-ink focus:border-accent focus:outline-none"
            >
              <option value="">— Select a field to zoom —</option>
              {allFields.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name} ({f.farmName}) · {fmt(f.areaHa)} ha
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
            <span className="font-medium text-ink-2">Quick Jump:</span>
            {PRESETS.map((p) => (
              <button
                key={p.name}
                type="button"
                onClick={() => jumpTo(p.lat, p.lon)}
                className="rounded border border-line bg-surface px-2 py-1 text-[11px] hover:bg-canvas hover:text-ink transition-colors"
              >
                {p.name}
              </button>
            ))}
          </div>
        </div>

        {/* Map Canvas with Floating Action Button */}
        <div className="relative">
          <div ref={mapContainerRef} className="h-[480px] w-full z-0" />

          {selectedFieldId && (
            <div className="absolute bottom-5 right-5 z-[1000]">
              <Link
                to={`/fields/${selectedFieldId}`}
                className="inline-flex items-center gap-2 rounded-xl bg-emerald-700 px-5 py-3 text-sm font-bold text-white shadow-xl ring-2 ring-white/90 hover:bg-emerald-800 transition-all transform hover:-translate-y-0.5 active:translate-y-0"
              >
                <Crosshair className="size-4" />
                Trace Farm Boundary
                <ArrowRight className="size-4" />
              </Link>
            </div>
          )}
        </div>

        {/* Elevated Primary Action Bar */}
        <div className="border-t border-slate-200 bg-gradient-to-r from-emerald-50/80 via-white to-slate-50 px-6 py-4">
          {selectedFieldId ? (
            (() => {
              const sf = allFields.find((f) => f.id === Number(selectedFieldId))
              if (!sf) return null
              return (
                <div className="flex flex-wrap items-center justify-between gap-4">
                  <div className="flex items-center gap-3">
                    <div className="rounded-lg bg-emerald-600/15 p-2 text-emerald-700">
                      <MapPin className="size-5" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-slate-900 text-base">{sf.name}</span>
                        <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold ${
                          sf.boundaryGeojson
                            ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                            : 'bg-amber-100 text-amber-800 border border-amber-300'
                        }`}>
                          {sf.boundaryGeojson ? '✓ Polygon Mapped' : '⚠ Boundary Not Traced'}
                        </span>
                      </div>
                      <p className="text-xs text-slate-500 mt-0.5">
                        {sf.farmName} · {fmt(sf.areaHa, 2)} ha ({Math.round(sf.areaHa * 2.47105 * 100) / 100} acres) · {sf.crop?.name || 'Crop not set'}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <Link
                      to={`/fields/${sf.id}/sustainability`}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 hover:text-slate-900 transition-colors"
                    >
                      📈 Multi-Season Dashboard
                    </Link>
                    <Link
                      to={`/fields/${sf.id}`}
                      className="inline-flex items-center gap-2 rounded-xl bg-emerald-700 px-5 py-2.5 text-sm font-bold text-white shadow-md hover:bg-emerald-800 transition-all transform active:scale-98"
                    >
                      <Crosshair className="size-4" />
                      Trace Farm Boundary
                      <ArrowRight className="size-4" />
                    </Link>
                  </div>
                </div>
              )
            })()
          ) : (
            <div className="flex flex-wrap items-center justify-between gap-4 text-sm text-slate-600">
              <div className="flex items-center gap-2">
                <Crosshair className="size-4 text-emerald-700" />
                <span>Select any parcel from the dropdown above to focus and launch GIS polygon tracing.</span>
              </div>
              {allFields.length > 0 && (
                <Link
                  to={`/fields/${allFields[0].id}`}
                  className="inline-flex items-center gap-2 rounded-xl bg-emerald-700 px-5 py-2 text-sm font-bold text-white shadow-md hover:bg-emerald-800 transition-all"
                >
                  <Crosshair className="size-4" />
                  Trace Farm Boundary
                </Link>
              )}
            </div>
          )}
        </div>
      </Panel>
    </>
  )
}
