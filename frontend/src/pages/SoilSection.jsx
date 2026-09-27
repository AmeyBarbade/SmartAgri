import { useState } from 'react'
import { toApiError } from '../api/client'
import { fieldApi, fieldToRequest, soilGridsApi } from '../api/endpoints'
import { Button, EmptyState, ErrorNotice, FieldError, Label, Panel, Section, Stat } from '../components/ui'
import { fmt, fmtDate, fmtFixed } from '../format'

// Sanity limits matching SoilRecordRequest on the backend (data-entry checks, not agronomic thresholds).
const LIMITS = {
  nitrogen: [0, 2000],
  phosphorus: [0, 1000],
  potassium: [0, 3000],
  ph: [3, 11],
  organicCarbon: [0, 20],
  sulfur: [0, 500],
  zinc: [0, 100],
  iron: [0, 200],
  copper: [0, 50],
  manganese: [0, 200],
  boron: [0, 50],
  ec: [0, 50],
}

const OPTIONAL = new Set(['organicCarbon', 'sulfur', 'zinc', 'iron', 'copper', 'manganese', 'boron', 'ec'])

const SOIL_TYPES = ['Sandy', 'Sandy loam', 'Loam', 'Silt loam', 'Clay loam', 'Clay', 'Black (vertisol)', 'Alluvial']

const today = () => new Date().toISOString().slice(0, 10)

export default function SoilSection({ field, soilRecords, onSaved }) {
  const latest = soilRecords[0] ?? null
  const [editing, setEditing] = useState(false)

  return (
    <Section
      title="Soil"
      description="Latest soil test for this field. Available N, P and K on an elemental basis."
      actions={
        !editing && latest && (
          <Button variant="secondary" onClick={() => setEditing(true)}>
            Edit soil data
          </Button>
        )
      }
    >
      {editing ? (
        <SoilForm
          field={field}
          latest={latest}
          onCancel={() => setEditing(false)}
          onSaved={() => {
            setEditing(false)
            onSaved()
          }}
        />
      ) : latest ? (
        <Panel className="px-5 py-4">
          <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-4">
            <Stat label="Available N">{fmt(latest.nitrogen)} kg/ha</Stat>
            <Stat label="Available P">{fmt(latest.phosphorus)} kg/ha</Stat>
            <Stat label="Available K">{fmt(latest.potassium)} kg/ha</Stat>
            <Stat label="pH">{fmtFixed(latest.ph, 1)}</Stat>
            <Stat label="Organic carbon">{latest.organicCarbon != null ? `${fmt(latest.organicCarbon)} %` : '—'}</Stat>
            <Stat label="Soil type">{field.soilType ?? 'Not recorded'}</Stat>
            <Stat label="Sample date">{fmtDate(latest.sampleDate)}</Stat>
            <Stat label="Tests on record">{soilRecords.length}</Stat>
            {latest.sulfur != null && <Stat label="Sulfur (S)">{fmt(latest.sulfur)} ppm</Stat>}
            {latest.zinc != null && <Stat label="Zinc (Zn)">{fmt(latest.zinc)} ppm</Stat>}
            {latest.iron != null && <Stat label="Iron (Fe)">{fmt(latest.iron)} ppm</Stat>}
            {latest.copper != null && <Stat label="Copper (Cu)">{fmt(latest.copper)} ppm</Stat>}
            {latest.manganese != null && <Stat label="Manganese (Mn)">{fmt(latest.manganese)} ppm</Stat>}
            {latest.boron != null && <Stat label="Boron (B)">{fmt(latest.boron)} ppm</Stat>}
            {latest.ec != null && <Stat label="EC">{fmt(latest.ec)} dS/m</Stat>}
          </dl>
        </Panel>
      ) : (
        <EmptyState
          title="No soil record available"
          action={
            <Button variant="secondary" onClick={() => setEditing(true)}>
              Add soil record
            </Button>
          }
        >
          Add a soil test before generating a recommendation. Without one, the nutrient engine assumes medium fertility
          and reports it as a warning.
        </EmptyState>
      )}
    </Section>
  )
}

function SoilForm({ field, latest, onCancel, onSaved }) {
  const [values, setValues] = useState({
    sampleDate: today(),
    nitrogen: latest?.nitrogen ?? '',
    phosphorus: latest?.phosphorus ?? '',
    potassium: latest?.potassium ?? '',
    ph: latest?.ph ?? '',
    organicCarbon: latest?.organicCarbon ?? '',
    sulfur: latest?.sulfur ?? '',
    zinc: latest?.zinc ?? '',
    iron: latest?.iron ?? '',
    copper: latest?.copper ?? '',
    manganese: latest?.manganese ?? '',
    boron: latest?.boron ?? '',
    ec: latest?.ec ?? '',
    soilType: field.soilType ?? '',
  })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [satelliteLoading, setSatelliteLoading] = useState(false)
  const [satelliteSource, setSatelliteSource] = useState(null)

  async function onAutoFillSatellite() {
    setSatelliteLoading(true)
    setError(null)
    try {
      const lat = field.farm?.latitude ?? 25.594
      const lon = field.farm?.longitude ?? 85.137
      const res = await soilGridsApi.fetch(lat, lon)
      setValues((prev) => ({
        ...prev,
        nitrogen: res.nitrogen_kg_ha != null ? String(res.nitrogen_kg_ha) : prev.nitrogen,
        ph: res.ph != null ? String(res.ph) : prev.ph,
        organicCarbon: res.organic_carbon_pct != null ? String(res.organic_carbon_pct) : prev.organicCarbon,
      }))
      setSatelliteSource(res.source)
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setSatelliteLoading(false)
    }
  }

  const set = (k) => (e) => setValues((v) => ({ ...v, [k]: e.target.value }))

  function validate() {
    const e = {}
    if (!values.sampleDate) e.sampleDate = 'Required.'
    else if (values.sampleDate > today()) e.sampleDate = 'Cannot be in the future.'
    for (const [k, [min, max]] of Object.entries(LIMITS)) {
      const raw = values[k]
      if (raw === '' || raw === null) {
        if (!OPTIONAL.has(k)) e[k] = 'Required.'
        continue
      }
      const n = Number(raw)
      if (Number.isNaN(n)) e[k] = 'Enter a number.'
      else if (n < min || n > max) e[k] = `Must be between ${min} and ${max}.`
    }
    return e
  }

  async function onSubmit(ev) {
    ev.preventDefault()
    const e = validate()
    setErrors(e)
    if (Object.keys(e).length) return
    setSaving(true)
    setError(null)
    const numOrNull = (k) => (values[k] === '' || values[k] === null ? null : Number(values[k]))
    try {
      await fieldApi.addSoilRecord(field.id, {
        sampleDate: values.sampleDate,
        nitrogen: Number(values.nitrogen),
        phosphorus: Number(values.phosphorus),
        potassium: Number(values.potassium),
        ph: Number(values.ph),
        organicCarbon: numOrNull('organicCarbon'),
        sulfur: numOrNull('sulfur'),
        zinc: numOrNull('zinc'),
        iron: numOrNull('iron'),
        copper: numOrNull('copper'),
        manganese: numOrNull('manganese'),
        boron: numOrNull('boron'),
        ec: numOrNull('ec'),
      })
      const soilType = values.soilType.trim() || null
      if (soilType !== (field.soilType ?? null)) {
        await fieldApi.update(field.id, fieldToRequest(field, { soilType }))
      }
      onSaved()
    } catch (err) {
      const apiErr = toApiError(err)
      setErrors(apiErr.fieldErrors ?? {})
      setError(apiErr)
    } finally {
      setSaving(false)
    }
  }

  const input = (name, { unit, step = 'any', label, hint }) => (
    <div>
      <Label htmlFor={`soil-${name}`} hint={hint}>
        {label}
      </Label>
      <div className="relative">
        <input
          id={`soil-${name}`}
          type="number"
          inputMode="decimal"
          step={step}
          className={`input tabular ${unit ? 'pr-14' : ''}`}
          value={values[name]}
          onChange={set(name)}
          aria-invalid={!!errors[name]}
        />
        {unit && (
          <span className="pointer-events-none absolute inset-y-0 right-2.5 flex items-center text-xs text-faint">{unit}</span>
        )}
      </div>
      <FieldError>{errors[name]}</FieldError>
    </div>
  )

  return (
    <Panel className="px-5 py-5">
      <form onSubmit={onSubmit} noValidate>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-line pb-3">
          <p className="text-[13px] text-muted">
            Saved as a new soil test. Earlier tests stay in the field's history.
          </p>
          <Button
            type="button"
            variant="secondary"
            loading={satelliteLoading}
            onClick={onAutoFillSatellite}
            className="text-xs"
          >
            🌍 Auto-Fill from Satellite (ISRIC)
          </Button>
        </div>
        {satelliteSource && (
          <div className="mb-4 rounded-md bg-emerald-50 px-3 py-2 text-xs text-emerald-800 border border-emerald-200">
            ✓ Pre-filled available N, pH, and Organic Carbon from <strong>{satelliteSource}</strong>.
          </div>
        )}
        {error && (
          <div className="mb-4">
            <ErrorNotice error={error} />
          </div>
        )}
        <fieldset className="grid gap-4 sm:grid-cols-3">
          <legend className="sr-only">Macronutrients</legend>
          {input('nitrogen', { label: 'Available N', unit: 'kg/ha' })}
          {input('phosphorus', { label: 'Available P', unit: 'kg/ha' })}
          {input('potassium', { label: 'Available K', unit: 'kg/ha' })}
        </fieldset>
        <div className="mt-4 grid gap-4 sm:grid-cols-3">
          {input('ph', { label: 'pH', step: '0.1' })}
          {input('organicCarbon', { label: 'Organic carbon', unit: '%', hint: 'optional' })}
          <div>
            <Label htmlFor="soil-sampleDate">Sample date</Label>
            <input
              id="soil-sampleDate"
              type="date"
              max={today()}
              className="input"
              value={values.sampleDate}
              onChange={set('sampleDate')}
              aria-invalid={!!errors.sampleDate}
            />
            <FieldError>{errors.sampleDate}</FieldError>
          </div>
        </div>

        <details className="mt-4 border-t border-line/60 pt-3">
          <summary className="cursor-pointer text-xs font-medium text-ink-2 hover:text-ink">
            Secondary & Micronutrients (optional: S, Zn, Fe, Cu, Mn, B, EC)
          </summary>
          <div className="mt-3 grid gap-4 sm:grid-cols-3">
            {input('sulfur', { label: 'Sulfur (S)', unit: 'ppm', hint: 'target > 10' })}
            {input('zinc', { label: 'Zinc (Zn)', unit: 'ppm', hint: 'target > 0.6' })}
            {input('iron', { label: 'Iron (Fe)', unit: 'ppm', hint: 'target > 4.5' })}
            {input('copper', { label: 'Copper (Cu)', unit: 'ppm', hint: 'target > 0.2' })}
            {input('manganese', { label: 'Manganese (Mn)', unit: 'ppm', hint: 'target > 2.0' })}
            {input('boron', { label: 'Boron (B)', unit: 'ppm', hint: 'target > 0.5' })}
            {input('ec', { label: 'Electrical Conductivity', unit: 'dS/m', hint: 'target < 1.0' })}
          </div>
        </details>

        <div className="mt-4 grid gap-4 sm:grid-cols-3">
          <div>
            <Label htmlFor="soil-type" hint="field attribute">
              Soil type
            </Label>
            <input
              id="soil-type"
              list="soil-type-options"
              className="input"
              maxLength={40}
              value={values.soilType}
              onChange={set('soilType')}
            />
            <datalist id="soil-type-options">
              {SOIL_TYPES.map((t) => (
                <option key={t} value={t} />
              ))}
            </datalist>
          </div>
        </div>
        <div className="mt-6 flex gap-3">
          <Button type="submit" variant="primary" loading={saving}>
            Save soil test
          </Button>
          <Button type="button" variant="secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}
