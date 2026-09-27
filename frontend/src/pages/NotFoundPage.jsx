import { ButtonLink, EmptyState } from '../components/ui'

export default function NotFoundPage() {
  return (
    <EmptyState title="Page not found" action={<ButtonLink to="/">Go to dashboard</ButtonLink>}>
      The address does not match any page.
    </EmptyState>
  )
}
