import { Route, Routes } from 'react-router-dom'
import AppLayout from './layout/AppLayout'
import CreateTicketView from './views/CreateTicketView'
import TicketDetailView from './views/TicketDetailView'
import TicketListView from './views/TicketListView'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<TicketListView />} />
        <Route path="tickets/new" element={<CreateTicketView />} />
        <Route path="tickets/:ticketId" element={<TicketDetailView />} />
      </Route>
    </Routes>
  )
}
