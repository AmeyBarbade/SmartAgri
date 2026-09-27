import { strategyLabel } from '../format'

/**
 * Collapses plans the backend marked as identical (`sameAs`) into one column.
 * No numbers are derived: each group shows its representative plan's figures as returned by the API.
 * The selected plan represents its group, so the highlighted column is always the recommended one.
 */
export function groupPlans(plans = []) {
  const groups = []
  for (const plan of plans) {
    const group = groups.find(
      (g) => g.members.some((m) => (m.sameAs ?? []).includes(plan.strategy)) || (plan.sameAs ?? []).some((s) => g.strategies.includes(s)),
    )
    if (group) {
      group.members.push(plan)
      group.strategies.push(plan.strategy)
      if (plan.selected) group.plan = plan
    } else {
      groups.push({ plan, members: [plan], strategies: [plan.strategy] })
    }
  }
  return groups.map((g) => ({
    ...g,
    selected: g.members.some((m) => m.selected),
    label: g.members.map((m) => m.label ?? strategyLabel(m.strategy)).join(' · '),
  }))
}
