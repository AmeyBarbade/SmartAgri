import { Route, Routes } from 'react-router-dom'
import { AuthProvider, RequireAuth } from './auth/AuthContext'
import AppShell from './components/AppShell'
import DashboardPage from './pages/DashboardPage'
import FarmsPage from './pages/FarmsPage'
import FieldPage from './pages/FieldPage'
import HistoryPage from './pages/HistoryPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import RecommendationPage from './pages/RecommendationPage'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="farms" element={<FarmsPage />} />
          <Route path="farms/:farmId" element={<FarmsPage />} />
          <Route path="fields/:fieldId" element={<FieldPage />} />
          <Route path="recommendations/:recommendationId" element={<RecommendationPage />} />
          <Route path="history" element={<HistoryPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}
