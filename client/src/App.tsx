import { Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import ProjectList from './pages/ProjectList'
import ApiList from './pages/ApiList'
import ScenarioList from './pages/ScenarioList'
import ScenarioEditor from './pages/ScenarioEditor'
import EnvironmentPage from './pages/EnvironmentPage'
import ReportList from './pages/ReportList'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<Navigate to="/projects" replace />} />
        <Route path="/projects" element={<ProjectList />} />
        <Route path="/projects/:projectId/apis" element={<ApiList />} />
        <Route path="/projects/:projectId/scenarios" element={<ScenarioList />} />
        <Route path="/projects/:projectId/scenarios/:scenarioId" element={<ScenarioEditor />} />
        <Route path="/projects/:projectId/environments" element={<EnvironmentPage />} />
        <Route path="/projects/:projectId/reports" element={<ReportList />} />
        <Route path="*" element={<Navigate to="/projects" replace />} />
      </Route>
    </Routes>
  )
}
