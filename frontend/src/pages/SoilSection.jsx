import { useState } from 'react'
import { toApiError } from '../api/client'
import { fieldApi, fieldToRequest } from '../api/endpoints'
import { Button, EmptyState, ErrorNotice, FieldError, Label, Panel, Section, Stat } from '../components/ui'
import { fmt, fmtDate, fmtFixed } from '../format'

// Same sanity limits as SoilRecordRequest on the backend (data-entry checks, not agronomic thresholds).
const LIMITS = {
  nitrogen: [0, 2000],
  phosphorus: [0, 1000],
  potassium: [0, 3000],
  ph: [3, 11],
  organicCarbon: [0, 20],
}

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
    soilType: field.soilType ?? '',
  })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  const set = (k) => (e) => setValues((v) => ({ ...v, [k]: e.target.value }))

  function validate() {
    const e = {}
    if (!values.sampleDate) e.sampleDate = 'Required.'
    else if (values.sampleDate > today()) e.sampleDate = 'Cannot be in the future.'
    for (const [k, [min, max]] of Object.entries(LIMITS)) {
      const raw = values[k]
      if (raw === '' || raw === null) {
        if (k !== 'organicCarbon') e[k] = 'Required.'
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
    try {
      await fieldApi.addSoilRecord(field.id, {
        sampleDate: values.sampleDate,
        nitrogen: Number(values.nitrogen),
        phosphorus: Number(values.phosphorus),
        potassium: Number(values.potassium),
        ph: Number(values.ph),
        organicCarbon: values.organicCarbon === '' ? null : Number(values.organicCarbon),
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
        <p className="mb-4 text-[13px] text-muted">
          Saved as a new soil test. Earlier tests stay in the field's history.
        </p>
        {error && (
          <div className="mb-4">
            <ErrorNotice error={error} />
          </div>
        )}
        <fieldset className="grid gap-4 sm:grid-cols-3">
          <legend className="sr-only">Nutrients</legend>
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
